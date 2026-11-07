package group_chat.model;

public class GroupMember {
    private String username;
    private String fullName;
    private String role; // "LEADER" hoặc "MEMBER"
    private String status; // "ONLINE" hoặc "OFFLINE"

    public GroupMember(String username, String fullName, String role, String status) {
        this.username = username;
        this.fullName = fullName;
        this.role = role;
        this.status = status;
    }

    public String getUsername() {
        return username;
    }

    public String getFullName() {
        return fullName;
    }

    public String getRole() {
        return role;
    }

    public String getStatus() {
        return status;
    }
}
