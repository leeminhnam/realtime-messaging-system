package group_chat.model;

import com.google.gson.JsonObject;

public class MemberItem {
    private final String username;
    private final String fullName;
    private final String role; // "LEADER" hoặc "MEMBER"
    private final String status; // "ONLINE" hoặc "OFFLINE"

    public MemberItem(String username, String fullName, String role, String status) {
        this.username = username;
        this.fullName = fullName;
        this.role = role;
        this.status = status;
    }

    // Đổi JSON do Server trả về thành đối tượng MemberItem
    public static MemberItem fromJson(JsonObject json) {
        return new MemberItem(
                json.get("username").getAsString(),
                json.get("fullName").getAsString(),
                json.get("role").getAsString(),
                json.get("status").getAsString());
    }

    public boolean isLeader() {
        return "LEADER".equalsIgnoreCase(role);
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
