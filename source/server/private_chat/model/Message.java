package private_chat.model;

public class Message {
    private int id;
    private int conversationId;
    private String senderUsername;
    private String content;
    private String sentAt;
    private boolean isRead;

    public Message() {
    }

    // Tin mới, chưa có id và sentAt
    public Message(int conversationId, String senderUsername, String content) {
        this.conversationId = conversationId;
        this.senderUsername = senderUsername;
        this.content = content;
        this.isRead = false;
    }

    // Đọc từ CSDL
    public Message(int id, int conversationId, String senderUsername, String content, String sentAt, boolean isRead) {
        this.id = id;
        this.conversationId = conversationId;
        this.senderUsername = senderUsername;
        this.content = content;
        this.sentAt = sentAt;
        this.isRead = isRead;
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

    public String getSenderUsername() {
        return senderUsername;
    }

    public void setSenderUsername(String senderUsername) {
        this.senderUsername = senderUsername;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getSentAt() {
        return sentAt;
    }

    public void setSentAt(String sentAt) {
        this.sentAt = sentAt;
    }

    public boolean isRead() {
        return isRead;
    }

    public void setRead(boolean read) {
        isRead = read;
    }

    @Override
    public String toString() {
        return "Message{" +
                "id=" + id +
                ", conversationId=" + conversationId +
                ", senderUsername='" + senderUsername + '\'' +
                ", content='" + content + '\'' +
                ", sentAt='" + sentAt + '\'' +
                ", isRead=" + isRead +
                '}';
    }
}
