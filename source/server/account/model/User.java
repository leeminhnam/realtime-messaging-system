package account.model;

import java.util.HashMap;
import java.util.Map;

/**
 * Dữ liệu người dùng an toàn để trả về Client (không lưu/không truyền hash mật khẩu).
 * Các khóa trong toMap() giữ nguyên giao thức JSON và giao diện DAO hiện tại.
 */
public class User {
    private final String username;
    private final String fullName;
    private final String avatar;
    private final String status;

    public User(String username, String fullName, String avatar, String status) {
        this.username = username;
        this.fullName = fullName;
        this.avatar = avatar;
        this.status = status;
    }

    public String getUsername() { return username; }
    public String getFullName() { return fullName; }
    public String getAvatar() { return avatar; }
    public String getStatus() { return status; }

    public Map<String, String> toMap() {
        Map<String, String> values = new HashMap<>();
        values.put("username", username);
        values.put("fullName", fullName);
        values.put("avatar", avatar);
        values.put("status", status);
        return values;
    }
}
