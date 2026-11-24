package account.model;

/** Đại diện một người dùng trong danh bạ của Client (không chứa mật khẩu). */
// Một dòng trong danh bạ
public class UserItem {
    private final String username;
    private final String fullName;
    private String status;

    public UserItem(String username, String fullName, String status) {
        this.username = username;
        this.fullName = fullName;
        this.status = status;
    }

    public String getUsername() {
        return username;
    }

    public String getFullName() {
        return fullName;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
