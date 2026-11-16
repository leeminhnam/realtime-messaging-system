package file_notification.model;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.text.DecimalFormat;

public class MediaContent {
    private static final Gson gson = new Gson();

    private boolean isMedia;
    private int fileId;
    private String fileType; // IMAGE, AUDIO, VIDEO, FILE
    private String fileName;
    private long fileSize;
    private int duration; // số giây, dùng cho ghi âm và video
    private String caption;

    public MediaContent() {
    }

    public MediaContent(int fileId, String fileType, String fileName, long fileSize, int duration, String caption) {
        this.isMedia = true;
        this.fileId = fileId;
        this.fileType = fileType;
        this.fileName = fileName;
        this.fileSize = fileSize;
        this.duration = duration;
        this.caption = caption != null ? caption : "";
    }

    // content là JSON mô tả tệp thì trả về MediaContent, tin chữ thì trả về null
    public static MediaContent parse(String content) {
        if (content == null || content.trim().isEmpty()) {
            return null;
        }

        String trimmed = content.trim();
        if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) {
            return null;
        }

        try {
            JsonObject obj = JsonParser.parseString(trimmed).getAsJsonObject();
            if (obj.has("isMedia") && obj.get("isMedia").getAsBoolean()) {
                MediaContent media = new MediaContent();
                media.isMedia = true;
                media.fileId = obj.has("fileId") ? obj.get("fileId").getAsInt() : 0;
                media.fileType = obj.has("fileType") ? obj.get("fileType").getAsString().toUpperCase() : "FILE";
                media.fileName = obj.has("fileName") ? obj.get("fileName").getAsString() : "file";
                media.fileSize = obj.has("fileSize") ? obj.get("fileSize").getAsLong() : 0;
                media.duration = obj.has("duration") ? obj.get("duration").getAsInt() : 0;
                media.caption = obj.has("caption") ? obj.get("caption").getAsString() : "";
                return media;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    // Đổi sang chuỗi JSON để lưu vào cột content
    public String toJson() {
        return gson.toJson(this);
    }

    public String getFormattedFileSize() {
        if (fileSize <= 0) return "0 B";
        final String[] units = new String[]{"B", "KB", "MB", "GB"};
        int digitGroups = (int) (Math.log10(fileSize) / Math.log10(1024));
        if (digitGroups >= units.length) digitGroups = units.length - 1;
        return new DecimalFormat("#,##0.#").format(fileSize / Math.pow(1024, digitGroups)) + " " + units[digitGroups];
    }

    public String getFormattedDuration() {
        int m = duration / 60;
        int s = duration % 60;
        return String.format("%02d:%02d", m, s);
    }

    public boolean isImage() {
        return "IMAGE".equalsIgnoreCase(fileType);
    }

    public boolean isAudio() {
        return "AUDIO".equalsIgnoreCase(fileType);
    }

    public boolean isVideo() {
        return "VIDEO".equalsIgnoreCase(fileType);
    }

    public boolean isFile() {
        return "FILE".equalsIgnoreCase(fileType);
    }

    // Getter, setter
    public boolean isMedia() {
        return isMedia;
    }

    public void setMedia(boolean media) {
        isMedia = media;
    }

    public int getFileId() {
        return fileId;
    }

    public void setFileId(int fileId) {
        this.fileId = fileId;
    }

    public String getFileType() {
        return fileType;
    }

    public void setFileType(String fileType) {
        this.fileType = fileType;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public long getFileSize() {
        return fileSize;
    }

    public void setFileSize(long fileSize) {
        this.fileSize = fileSize;
    }

    public int getDuration() {
        return duration;
    }

    public void setDuration(int duration) {
        this.duration = duration;
    }

    public String getCaption() {
        return caption;
    }

    public void setCaption(String caption) {
        this.caption = caption;
    }
}
