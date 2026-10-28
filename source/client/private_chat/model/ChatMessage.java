package private_chat.model;

import com.google.gson.JsonObject;

public class ChatMessage {
    private int id;
    private int conversationId;
    private String sender;
    private String receiver;
    private String content;
    private String sentAt;
    private boolean isMine;
    private boolean isRead;

    public ChatMessage() {
    }

    public ChatMessage(int id, int conversationId, String sender, String receiver, String content, String sentAt,
            boolean isMine, boolean isRead) {
        this.id = id;
        this.conversationId = conversationId;
        this.sender = sender;
        this.receiver = receiver;
        this.content = content;
        this.sentAt = sentAt;
        this.isMine = isMine;
        this.isRead = isRead;
    }

    /**
     * Tiện ích parse từ JsonObject do Server trả về thành đối tượng ChatMessage.
     * Tự động xác định cờ isMine dựa vào currentUsername.
     *
     * @param json            Gói tin JSON từ Server
     * @param currentUsername Tên tài khoản của người dùng trên Client này
     * @return Đối tượng ChatMessage
     */
    public static ChatMessage fromJson(JsonObject json, String currentUsername) {
        if (json == null) {
            return null;
        }

        int id = json.has("id") && !json.get("id").isJsonNull() ? json.get("id").getAsInt() : 0;
        int convId = json.has("conversationId") && !json.get("conversationId").isJsonNull()
                ? json.get("conversationId").getAsInt()
                : 0;

        String sender = "";
        if (json.has("sender") && !json.get("sender").isJsonNull()) {
            sender = json.get("sender").getAsString();
        } else if (json.has("senderUsername") && !json.get("senderUsername").isJsonNull()) {
            sender = json.get("senderUsername").getAsString();
        }

        String receiver = json.has("receiver") && !json.get("receiver").isJsonNull()
                ? json.get("receiver").getAsString()
                : "";
        String content = json.has("content") && !json.get("content").isJsonNull()
                ? json.get("content").getAsString()
                : "";
        String sentAt = json.has("sentAt") && !json.get("sentAt").isJsonNull()
                ? json.get("sentAt").getAsString()
                : "";
        boolean isRead = json.has("isRead") && !json.get("isRead").isJsonNull() && json.get("isRead").getAsBoolean();

        boolean isMine = sender.equalsIgnoreCase(currentUsername);

        return new ChatMessage(id, convId, sender, receiver, content, sentAt, isMine, isRead);
    }

    /**
     * Lấy giờ:phút ngắn gọn từ sentAt để hiển thị chân bóng chat (VD: "15:30").
     */
    public String getFormattedTime() {
        if (sentAt == null || sentAt.trim().isEmpty()) {
            return "";
        }
        try {
            // Định dạng chuỗi "yyyy-MM-dd HH:mm:ss"
            if (sentAt.contains(" ") && sentAt.length() >= 16) {
                return sentAt.substring(11, 16);
            }
        } catch (Exception ignored) {
        }
        return sentAt;
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

    public String getSender() {
        return sender;
    }

    public void setSender(String sender) {
        this.sender = sender;
    }

    public String getReceiver() {
        return receiver;
    }

    public void setReceiver(String receiver) {
        this.receiver = receiver;
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

    public boolean isMine() {
        return isMine;
    }

    public void setMine(boolean mine) {
        isMine = mine;
    }

    public boolean isRead() {
        return isRead;
    }

    public void setRead(boolean read) {
        isRead = read;
    }

    @Override
    public String toString() {
        return "ChatMessage{" +
                "id=" + id +
                ", sender='" + sender + '\'' +
                ", content='" + content + '\'' +
                ", isMine=" + isMine +
                ", sentAt='" + sentAt + '\'' +
                '}';
    }
}
