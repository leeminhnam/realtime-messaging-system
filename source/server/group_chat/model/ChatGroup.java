package group_chat.model;

public class ChatGroup {
    private int id;
    private String name;
    private String leaderUsername;
    private int memberCount;
    private String createdAt;

    public ChatGroup(int id, String name, String leaderUsername, int memberCount, String createdAt) {
        this.id = id;
        this.name = name;
        this.leaderUsername = leaderUsername;
        this.memberCount = memberCount;
        this.createdAt = createdAt;
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getLeaderUsername() {
        return leaderUsername;
    }

    public int getMemberCount() {
        return memberCount;
    }

    public String getCreatedAt() {
        return createdAt;
    }
}
