package private_chat.model;

public class Conversation {
    private int id;
    private String user1Username;
    private String user2Username;
    private String createdAt;
    private String updatedAt;

    public Conversation() {
    }

    public Conversation(int id, String user1Username, String user2Username, String createdAt, String updatedAt) {
        this.id = id;
        this.user1Username = user1Username;
        this.user2Username = user2Username;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getUser1Username() {
        return user1Username;
    }

    public void setUser1Username(String user1Username) {
        this.user1Username = user1Username;
    }

    public String getUser2Username() {
        return user2Username;
    }

    public void setUser2Username(String user2Username) {
        this.user2Username = user2Username;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(String updatedAt) {
        this.updatedAt = updatedAt;
    }

    // Người còn lại trong hội thoại
    public String getOtherUser(String currentUsername) {
        if (currentUsername == null) return null;
        if (currentUsername.equalsIgnoreCase(user1Username)) {
            return user2Username;
        }
        return user1Username;
    }

    @Override
    public String toString() {
        return "Conversation{" +
                "id=" + id +
                ", user1='" + user1Username + '\'' +
                ", user2='" + user2Username + '\'' +
                ", createdAt='" + createdAt + '\'' +
                ", updatedAt='" + updatedAt + '\'' +
                '}';
    }
}
