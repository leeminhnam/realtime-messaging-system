package group_chat.model;

public class GroupMessage {
    private int id;
    private int groupId;
    private String senderUsername;
    private String senderFullName;
    private String content;
    private String sentAt;

    public GroupMessage(int id, int groupId, String senderUsername, String senderFullName, String content,
            String sentAt) {
        this.id = id;
        this.groupId = groupId;
        this.senderUsername = senderUsername;
        this.senderFullName = senderFullName;
        this.content = content;
        this.sentAt = sentAt;
    }

    public int getId() {
        return id;
    }

    public int getGroupId() {
        return groupId;
    }

    public String getSenderUsername() {
        return senderUsername;
    }

    public String getSenderFullName() {
        return senderFullName;
    }

    public String getContent() {
        return content;
    }

    public String getSentAt() {
        return sentAt;
    }
}
