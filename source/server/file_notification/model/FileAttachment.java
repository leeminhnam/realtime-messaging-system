package file_notification.model;

public class FileAttachment {
    private int id;
    private int conversationId;
    private int messageId;
    private String senderUsername;
    private String receiverUsername;
    private String fileName;
    private String fileType; // IMAGE, AUDIO, VIDEO, FILE
    private long fileSize;
    private String storedPath;
    private String createdAt;

    public FileAttachment() {
    }

    public FileAttachment(int id, int conversationId, int messageId, String senderUsername, String receiverUsername,
                          String fileName, String fileType, long fileSize, String storedPath, String createdAt) {
        this.id = id;
        this.conversationId = conversationId;
        this.messageId = messageId;
        this.senderUsername = senderUsername;
        this.receiverUsername = receiverUsername;
        this.fileName = fileName;
        this.fileType = fileType;
        this.fileSize = fileSize;
        this.storedPath = storedPath;
        this.createdAt = createdAt;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getConversationId() {
        return conversationId;
    }

    public void setConversationId(int conversationId) {
        this.conversationId = conversationId;
    }

    public int getMessageId() {
        return messageId;
    }

    public void setMessageId(int messageId) {
        this.messageId = messageId;
    }

    public String getSenderUsername() {
        return senderUsername;
    }

    public void setSenderUsername(String senderUsername) {
        this.senderUsername = senderUsername;
    }

    public String getReceiverUsername() {
        return receiverUsername;
    }

    public void setReceiverUsername(String receiverUsername) {
        this.receiverUsername = receiverUsername;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getFileType() {
        return fileType;
    }

    public void setFileType(String fileType) {
        this.fileType = fileType;
    }

    public long getFileSize() {
        return fileSize;
    }

    public void setFileSize(long fileSize) {
        this.fileSize = fileSize;
    }

    public String getStoredPath() {
        return storedPath;
    }

    public void setStoredPath(String storedPath) {
        this.storedPath = storedPath;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }
}
