package file_notification.handler;

import account.UserDAO;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import file_notification.dao.FileAttachmentDAO;
import file_notification.model.FileAttachment;
import private_chat.dao.ConversationDAO;
import private_chat.dao.MessageDAO;
import private_chat.model.Conversation;
import private_chat.model.Message;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

public class FileNotificationHandler {
    private static final Gson gson = new Gson();
    // Cùng thư mục dữ liệu với DatabaseManager (có thể ghi đè bằng -Dchatnest.db.dir khi chạy test)
    private static final String UPLOAD_DIR = System.getProperty("chatnest.db.dir", "source/server/data") + "/uploads";

    /**
     * Kiểm tra xem action có thuộc Module File & Notification không.
     */
    public static boolean isFileNotificationAction(String action) {
        return "SEND_FILE".equalsIgnoreCase(action)
                || "DOWNLOAD_FILE".equalsIgnoreCase(action);
    }

    /**
     * Xử lý chính các yêu cầu của Module 4.
     */
    public static void handleFileNotificationAction(JsonObject req, PrintWriter writer, String currentUsername,
                                                    Map<String, PrintWriter> onlineClients) {
        if (currentUsername == null || currentUsername.trim().isEmpty()) {
            sendResponse(writer, "ERROR", "FAILED", "Bạn cần đăng nhập để thao tác tệp và thông báo!", null);
            return;
        }

        String action = req.has("action") ? req.get("action").getAsString() : "";
        JsonObject data = req.has("data") && !req.get("data").isJsonNull() ? req.getAsJsonObject("data") : new JsonObject();

        switch (action) {
            case "SEND_FILE":
                handleSendFile(data, writer, currentUsername, onlineClients);
                break;

            case "DOWNLOAD_FILE":
                handleDownloadFile(data, writer);
                break;

            default:
                sendResponse(writer, "ERROR", "FAILED", "Hành động file không hợp lệ!", null);
                break;
        }
    }

    /**
     * Xử lý khi Client gửi file (ảnh, video, audio, tài liệu).
     */
    private static void handleSendFile(JsonObject data, PrintWriter senderWriter, String senderUsername,
                                       Map<String, PrintWriter> onlineClients) {
        if (!data.has("receiver") || !data.has("fileName") || !data.has("base64Data")) {
            sendResponse(senderWriter, "SEND_FILE_RESPONSE", "FAILED", "Thiếu thông tin tệp gửi!", null);
            return;
        }

        String receiverUsername = data.get("receiver").getAsString().trim();
        String originalFileName = data.get("fileName").getAsString().trim();
        String fileType = data.has("fileType") ? data.get("fileType").getAsString().toUpperCase() : "FILE";
        String base64Data = data.get("base64Data").getAsString();
        int duration = data.has("duration") ? data.get("duration").getAsInt() : 0;
        String caption = data.has("caption") ? data.get("caption").getAsString() : "";

        if (receiverUsername.isEmpty() || originalFileName.isEmpty() || base64Data.isEmpty()) {
            sendResponse(senderWriter, "SEND_FILE_RESPONSE", "FAILED", "Dữ liệu tệp không hợp lệ!", null);
            return;
        }

        if (receiverUsername.equalsIgnoreCase(senderUsername)) {
            sendResponse(senderWriter, "SEND_FILE_RESPONSE", "FAILED", "Không thể tự gửi file cho chính mình!", null);
            return;
        }

        try {
            // 1. Giải mã Base64 thành byte
            byte[] fileBytes = Base64.getDecoder().decode(base64Data);
            long fileSize = fileBytes.length;

            // Đảm bảo thư mục upload tồn tại
            File uploadFolder = new File(UPLOAD_DIR);
            if (!uploadFolder.exists()) {
                uploadFolder.mkdirs();
            }

            // Tạo tên file lưu trữ duy nhất để tránh trùng lặp
            String safeFileName = originalFileName.replaceAll("[^a-zA-Z0-9._-]", "_");
            String storedFileName = System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 8) + "_" + safeFileName;
            File targetFile = new File(uploadFolder, storedFileName);

            // Lưu file ra đĩa
            try (FileOutputStream fos = new FileOutputStream(targetFile)) {
                fos.write(fileBytes);
            }

            // 2. Tìm hoặc tạo cuộc trò chuyện giữa 2 người
            Conversation conv = ConversationDAO.getOrCreateConversation(senderUsername, receiverUsername);
            if (conv == null) {
                sendResponse(senderWriter, "SEND_FILE_RESPONSE", "FAILED", "Không thể tạo cuộc hội thoại!", null);
                return;
            }

            // 3. Lưu thông tin tệp vào CSDL (bảng file_attachments)
            FileAttachment attachment = FileAttachmentDAO.saveFileAttachment(
                    conv.getId(), 0, senderUsername, receiverUsername, originalFileName, fileType, fileSize, targetFile.getAbsolutePath()
            );

            if (attachment == null) {
                sendResponse(senderWriter, "SEND_FILE_RESPONSE", "FAILED", "Lỗi lưu thông tin tệp vào CSDL!", null);
                return;
            }

            // 4. Tạo nội dung tin nhắn dạng cấu trúc JSON Media
            JsonObject mediaContentJson = new JsonObject();
            mediaContentJson.addProperty("isMedia", true);
            mediaContentJson.addProperty("fileId", attachment.getId());
            mediaContentJson.addProperty("fileType", fileType);
            mediaContentJson.addProperty("fileName", originalFileName);
            mediaContentJson.addProperty("fileSize", fileSize);
            mediaContentJson.addProperty("duration", duration);
            mediaContentJson.addProperty("caption", caption);

            String contentString = gson.toJson(mediaContentJson);

            // Lưu vào bảng messages
            Message savedMsg = MessageDAO.saveMessage(conv.getId(), senderUsername, contentString);
            if (savedMsg != null) {
                FileAttachmentDAO.updateMessageId(attachment.getId(), savedMsg.getId());
            }

            // 5. Chuẩn bị gói dữ liệu phản hồi
            JsonObject resData = new JsonObject();
            resData.addProperty("id", savedMsg != null ? savedMsg.getId() : 0);
            resData.addProperty("conversationId", conv.getId());
            resData.addProperty("sender", senderUsername);
            resData.addProperty("receiver", receiverUsername);
            resData.addProperty("content", contentString);
            resData.addProperty("sentAt", savedMsg != null ? savedMsg.getSentAt() : "");
            resData.addProperty("fileId", attachment.getId());
            resData.addProperty("fileType", fileType);
            resData.addProperty("fileName", originalFileName);
            resData.addProperty("fileSize", fileSize);
            resData.addProperty("duration", duration);

            // Phản hồi thành công cho người gửi
            sendResponse(senderWriter, "SEND_FILE_RESPONSE", "SUCCESS", "Gửi tệp thành công!", resData);
            System.out.println("[FILE UPLOAD] @" + senderUsername + " đã gửi tệp [" + fileType + "] "
                    + originalFileName + " (" + (fileSize / 1024) + " KB) tới @" + receiverUsername);

            // 6. Chuyển tiếp thời gian thực cho người nhận (nếu đang Online)
            PrintWriter receiverWriter = onlineClients.get(receiverUsername);
            if (receiverWriter != null) {
                // a. Gửi tin nhắn mới để hiển thị ngay trong khung chat của người nhận
                JsonObject receiveMsgPacket = new JsonObject();
                receiveMsgPacket.addProperty("action", "RECEIVE_PRIVATE_MESSAGE");
                receiveMsgPacket.addProperty("status", "SUCCESS");
                receiveMsgPacket.add("data", resData);
                receiverWriter.println(gson.toJson(receiveMsgPacket));

                // b. Gửi thông báo nổi thời gian thực (Realtime Toast Notification)
                Map<String, String> senderInfo = UserDAO.getUserByUsername(senderUsername);
                String senderFullName = senderInfo != null ? senderInfo.get("fullName") : senderUsername;

                String typeDesc;
                switch (fileType) {
                    case "IMAGE":
                        typeDesc = "hình ảnh 🖼️";
                        break;
                    case "AUDIO":
                        typeDesc = "đoạn ghi âm 🎙️ (" + duration + "s)";
                        break;
                    case "VIDEO":
                        typeDesc = "video 🎬";
                        break;
                    default:
                        typeDesc = "tệp tin 📄: " + originalFileName;
                        break;
                }

                JsonObject notifPacket = new JsonObject();
                notifPacket.addProperty("action", "RECEIVE_NOTIFICATION");
                notifPacket.addProperty("status", "SUCCESS");
                JsonObject notifData = new JsonObject();
                notifData.addProperty("type", "FILE");
                notifData.addProperty("fileType", fileType);
                notifData.addProperty("sender", senderUsername);
                notifData.addProperty("senderFullName", senderFullName);
                notifData.addProperty("fileName", originalFileName);
                notifData.addProperty("fileId", attachment.getId());
                notifData.addProperty("message", senderFullName + " đã gửi cho bạn một " + typeDesc);
                notifPacket.add("data", notifData);

                receiverWriter.println(gson.toJson(notifPacket));
                System.out.println("[NOTIFICATION] Đã gửi thông báo tệp mới tới @" + receiverUsername);
            }

        } catch (Exception e) {
            System.err.println("[FILE LỖI] Lỗi xử lý gửi tệp: " + e.getMessage());
            e.printStackTrace();
            sendResponse(senderWriter, "SEND_FILE_RESPONSE", "FAILED", "Lỗi server khi lưu tệp: " + e.getMessage(), null);
        }
    }

    /**
     * Xử lý khi Client yêu cầu tải nội dung file theo fileId.
     */
    private static void handleDownloadFile(JsonObject data, PrintWriter writer) {
        if (!data.has("fileId")) {
            sendResponse(writer, "DOWNLOAD_FILE_RESPONSE", "FAILED", "Thiếu fileId!", null);
            return;
        }

        int fileId = data.get("fileId").getAsInt();
        FileAttachment attachment = FileAttachmentDAO.getFileAttachmentById(fileId);

        if (attachment == null) {
            sendResponse(writer, "DOWNLOAD_FILE_RESPONSE", "FAILED", "Không tìm thấy tệp yêu cầu!", null);
            return;
        }

        File file = new File(attachment.getStoredPath());
        if (!file.exists()) {
            sendResponse(writer, "DOWNLOAD_FILE_RESPONSE", "FAILED", "Tệp không tồn tại trên máy chủ lưu trữ!", null);
            return;
        }

        try {
            byte[] fileBytes = Files.readAllBytes(file.toPath());
            String base64Data = Base64.getEncoder().encodeToString(fileBytes);

            JsonObject resData = new JsonObject();
            resData.addProperty("fileId", attachment.getId());
            resData.addProperty("fileName", attachment.getFileName());
            resData.addProperty("fileType", attachment.getFileType());
            resData.addProperty("fileSize", attachment.getFileSize());
            resData.addProperty("base64Data", base64Data);

            sendResponse(writer, "DOWNLOAD_FILE_RESPONSE", "SUCCESS", "Tải tệp thành công!", resData);
            System.out.println("[FILE DOWNLOAD] Đã gửi tệp #" + fileId + " (" + attachment.getFileName() + ") cho Client");
        } catch (IOException e) {
            System.err.println("[FILE LỖI] Đọc tệp thất bại: " + e.getMessage());
            sendResponse(writer, "DOWNLOAD_FILE_RESPONSE", "FAILED", "Không thể đọc dữ liệu tệp từ đĩa!", null);
        }
    }

    /**
     * Tiện ích gửi phản hồi JSON chuẩn.
     */
    private static void sendResponse(PrintWriter writer, String action, String status, String message, JsonObject data) {
        JsonObject res = new JsonObject();
        res.addProperty("action", action);
        res.addProperty("status", status);
        res.addProperty("message", message);
        if (data != null) {
            res.add("data", data);
        }
        writer.println(gson.toJson(res));
    }
}
