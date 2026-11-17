package file_notification.controller;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class FileClientManager {
    private static FileClientManager instance;
    private final Gson gson = new Gson();

    // Cache các tệp đã tải: fileId -> dữ liệu
    private final Map<Integer, byte[]> fileCache = new ConcurrentHashMap<>();

    // Danh sách callback đang chờ dữ liệu của từng fileId
    private final Map<Integer, List<Consumer<byte[]>>> downloadListeners = new ConcurrentHashMap<>();

    private PrintWriter writer;

    public static synchronized FileClientManager getInstance() {
        if (instance == null) {
            instance = new FileClientManager();
        }
        return instance;
    }

    public void setWriter(PrintWriter writer) {
        this.writer = writer;
    }

    public PrintWriter getWriter() {
        return this.writer;
    }

    // Gửi một tệp trên máy lên Server
    public boolean sendFile(PrintWriter writer, String receiver, File file, String fileType, int duration, String caption) {
        byte[] fileBytes = readForUpload(file);
        return fileBytes != null && sendBytes(writer, receiver, fileBytes, file.getName(), fileType, duration, caption);
    }

    // Gửi dữ liệu đã có trong bộ nhớ, ví dụ đoạn ghi âm
    public boolean sendBytes(PrintWriter writer, String receiver, byte[] bytes, String fileName, String fileType,
                             int duration, String caption) {
        JsonObject target = new JsonObject();
        target.addProperty("receiver", receiver);
        return sendPacket(writer, "SEND_FILE", target, bytes, fileName, fileType, duration, caption);
    }

    // Gửi một tệp trên máy vào nhóm
    public boolean sendGroupFile(PrintWriter writer, int groupId, File file, String fileType) {
        byte[] fileBytes = readForUpload(file);
        return fileBytes != null && sendGroupBytes(writer, groupId, fileBytes, file.getName(), fileType, 0);
    }

    // Gửi dữ liệu trong bộ nhớ (ví dụ đoạn ghi âm) vào nhóm
    public boolean sendGroupBytes(PrintWriter writer, int groupId, byte[] bytes, String fileName, String fileType,
                                  int duration) {
        JsonObject target = new JsonObject();
        target.addProperty("groupId", groupId);
        return sendPacket(writer, "SEND_GROUP_FILE", target, bytes, fileName, fileType, duration, "");
    }

    // Đọc tệp để gửi, tối đa 20 MB vì cả tệp nằm trong một dòng JSON
    private byte[] readForUpload(File file) {
        if (file == null || !file.exists()) {
            return null;
        }
        if (file.length() > 20 * 1024 * 1024) {
            JOptionPane.showMessageDialog(null, "Dung lượng tệp vượt quá giới hạn 20MB!", "Cảnh báo", JOptionPane.WARNING_MESSAGE);
            return null;
        }
        try {
            return Files.readAllBytes(file.toPath());
        } catch (IOException e) {
            System.err.println("[FILE CLIENT] Lỗi đọc file để gửi: " + e.getMessage());
            return null;
        }
    }

    // Đóng gói tệp thành JSON (Base64) rồi gửi. target chứa receiver hoặc groupId
    private boolean sendPacket(PrintWriter writer, String action, JsonObject target, byte[] bytes, String fileName,
                               String fileType, int duration, String caption) {
        if (writer == null || bytes == null || bytes.length == 0) {
            return false;
        }

        try {
            String base64Data = Base64.getEncoder().encodeToString(bytes);

            JsonObject req = new JsonObject();
            req.addProperty("action", action);
            JsonObject data = target.deepCopy();
            data.addProperty("fileName", fileName);
            data.addProperty("fileType", fileType != null ? fileType.toUpperCase() : "FILE");
            data.addProperty("fileSize", bytes.length);
            data.addProperty("duration", duration);
            data.addProperty("caption", caption != null ? caption : "");
            data.addProperty("base64Data", base64Data);
            req.add("data", data);

            writer.println(gson.toJson(req));
            return true;
        } catch (Exception e) {
            System.err.println("[FILE CLIENT] Lỗi mã hóa gửi file: " + e.getMessage());
            return false;
        }
    }

    // Tải tệp theo fileId, dùng writer mặc định
    public void getFileData(int fileId, Consumer<byte[]> callback) {
        getFileData(this.writer, fileId, callback);
    }

    // Có trong cache thì trả luôn, chưa có thì gửi DOWNLOAD_FILE
    public void getFileData(PrintWriter writer, int fileId, Consumer<byte[]> callback) {
        if (fileId <= 0) return;

        // Đã có trong cache
        byte[] cached = fileCache.get(fileId);
        if (cached != null) {
            if (callback != null) {
                SwingUtilities.invokeLater(() -> callback.accept(cached));
            }
            return;
        }

        // Thêm callback vào danh sách chờ
        downloadListeners.computeIfAbsent(fileId, k -> Collections.synchronizedList(new ArrayList<>())).add(callback);

        // Chỉ callback đầu tiên mới gửi yêu cầu, tránh tải một tệp nhiều lần
        if (downloadListeners.get(fileId).size() == 1 && writer != null) {
            JsonObject req = new JsonObject();
            req.addProperty("action", "DOWNLOAD_FILE");
            JsonObject data = new JsonObject();
            data.addProperty("fileId", fileId);
            req.add("data", data);
            writer.println(gson.toJson(req));
        }
    }

    // Nhận DOWNLOAD_FILE_RESPONSE
    public void onDownloadResponse(JsonObject resData) {
        if (resData == null || !resData.has("fileId") || !resData.has("base64Data")) {
            return;
        }

        try {
            int fileId = resData.get("fileId").getAsInt();
            String base64 = resData.get("base64Data").getAsString();
            byte[] fileBytes = Base64.getDecoder().decode(base64);

            // Lưu vào cache
            fileCache.put(fileId, fileBytes);

            // Gọi lại mọi chỗ đang chờ tệp này
            List<Consumer<byte[]>> callbacks = downloadListeners.remove(fileId);
            if (callbacks != null) {
                for (Consumer<byte[]> cb : callbacks) {
                    if (cb != null) {
                        SwingUtilities.invokeLater(() -> cb.accept(fileBytes));
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[FILE CLIENT] Lỗi phân giải dữ liệu download: " + e.getMessage());
        }
    }

    // Tệp mình vừa gửi thì cho luôn vào cache, khỏi tải lại
    public void cacheFileBytes(int fileId, byte[] bytes) {
        if (fileId > 0 && bytes != null) {
            fileCache.put(fileId, bytes);
        }
    }

    // Lưu tệp về máy
    public void saveFileToDisk(Component parent, String fileName, byte[] data) {
        if (data == null || data.length == 0) {
            JOptionPane.showMessageDialog(parent, "Dữ liệu tệp chưa sẵn sàng hoặc rỗng!", "Lỗi", JOptionPane.ERROR_MESSAGE);
            return;
        }

        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new File(fileName));
        int res = chooser.showSaveDialog(parent);
        if (res == JFileChooser.APPROVE_OPTION) {
            File target = chooser.getSelectedFile();
            try (FileOutputStream fos = new FileOutputStream(target)) {
                fos.write(data);
                JOptionPane.showMessageDialog(parent, "Đã lưu tệp thành công:\n" + target.getAbsolutePath(),
                        "Thành công", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(parent, "Lỗi khi ghi tệp: " + ex.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    // Mở video bằng trình phát mặc định của hệ điều hành
    public void openVideo(Component parent, String fileName, byte[] data) {
        if (data == null || data.length == 0) {
            JOptionPane.showMessageDialog(parent, "Dữ liệu video chưa sẵn sàng!", "Lỗi", JOptionPane.ERROR_MESSAGE);
            return;
        }

        try {
            // Ghi ra tệp tạm trước
            String cleanName = fileName != null ? fileName.replaceAll("[^a-zA-Z0-9._-]", "_") : "video.mp4";
            File tempVideo = File.createTempFile("chat_video_", "_" + cleanName);
            tempVideo.deleteOnExit();

            try (FileOutputStream fos = new FileOutputStream(tempVideo)) {
                fos.write(data);
            }

            // Mở bằng chương trình mặc định để phát
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                Desktop.getDesktop().open(tempVideo);
            } else {
                JOptionPane.showMessageDialog(parent, "Hệ thống không hỗ trợ mở tự động. Vui lòng nhấn Lưu tệp!", "Thông báo", JOptionPane.INFORMATION_MESSAGE);
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(parent, "Không thể mở video: " + ex.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
        }
    }
}
