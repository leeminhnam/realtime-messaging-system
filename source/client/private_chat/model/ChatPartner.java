package private_chat.model;

public class ChatPartner {
    private String username;
    private String fullName;
    private String status;
    private String avatar;

    public ChatPartner() {
    }

    public ChatPartner(String username, String fullName, String status, String avatar) {
        this.username = username;
        this.fullName = fullName;
        this.status = status;
        this.avatar = avatar;
    }

    public ChatPartner(String username, String fullName, String status) {
        this(username, fullName, status, "");
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getAvatar() {
        return avatar;
    }

    public void setAvatar(String avatar) {
        this.avatar = avatar;
    }

    public String getDisplayName() {
        if (fullName != null && !fullName.trim().isEmpty()) {
            return fullName;
        }
        return username;
    }

    @Override
    public String toString() {
        return "ChatPartner{" +
                "username='" + username + '\'' +
                ", fullName='" + fullName + '\'' +
                ", status='" + status + '\'' +
                '}';
    }
}
