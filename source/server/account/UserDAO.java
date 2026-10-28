package account;

import database.DatabaseManager;
import org.mindrot.jbcrypt.BCrypt;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class UserDAO {

    // 1. Đăng ký tài khoản mới (Hash mật khẩu bằng BCrypt)
    public static boolean register(String username, String password, String fullName) {
        String sql = "INSERT INTO users(username, password, full_name, status) VALUES(?, ?, ?, 'OFFLINE')";

        // Hash mật khẩu
        String hashedPassword = BCrypt.hashpw(password, BCrypt.gensalt());

        try (Connection conn = DatabaseManager.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username);
            pstmt.setString(2, hashedPassword);
            pstmt.setString(3, fullName);
            pstmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            System.err.println("[UserDAO] Lỗi đăng ký: " + e.getMessage());
            return false;
        }
    }

    // 2. Xác thực đăng nhập
    public static Map<String, String> login(String username, String password) {
        String sql = "SELECT * FROM users WHERE username = ?";

        try (Connection conn = DatabaseManager.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                String storedHash = rs.getString("password");
                // So sánh mật khẩu nhập vào với mã hash trong DB
                if (BCrypt.checkpw(password, storedHash)) {
                    // Cập nhật trạng thái thành ONLINE
                    updateStatus(username, "ONLINE");

                    Map<String, String> user = new HashMap<>();
                    user.put("username", rs.getString("username"));
                    user.put("fullName", rs.getString("full_name"));
                    user.put("avatar", rs.getString("avatar"));
                    user.put("status", "ONLINE");
                    return user;
                }
            }
        } catch (SQLException e) {
            System.err.println("[UserDAO] Lỗi đăng nhập: " + e.getMessage());
        }
        return null; // Đăng nhập thất bại
    }

    // 3. Cập nhật trạng thái ONLINE / OFFLINE
    public static void updateStatus(String username, String status) {
        String sql = "UPDATE users SET status = ? WHERE username = ?";
        try (Connection conn = DatabaseManager.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, status);
            pstmt.setString(2, username);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[UserDAO] Lỗi cập nhật status: " + e.getMessage());
        }
    }

    // 4. Lấy danh sách tất cả người dùng (phục vụ xem danh sách & theo dõi trạng thái)
    public static List<Map<String, String>> getAllUsers() {
        List<Map<String, String>> userList = new ArrayList<>();
        String sql = "SELECT username, full_name, avatar, status FROM users";

        try (Connection conn = DatabaseManager.getConnection();
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                Map<String, String> user = new HashMap<>();
                user.put("username", rs.getString("username"));
                user.put("fullName", rs.getString("full_name"));
                user.put("avatar", rs.getString("avatar"));
                user.put("status", rs.getString("status"));
                userList.add(user);
            }
        } catch (SQLException e) {
            System.err.println("[UserDAO] Lỗi lấy danh sách user: " + e.getMessage());
        }
        return userList;
    }

    // 5. Cập nhật hồ sơ cá nhân
    public static boolean updateProfile(String username, String fullName, String avatar) {
        String sql = "UPDATE users SET full_name = ?, avatar = ? WHERE username = ?";
        try (Connection conn = DatabaseManager.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, fullName);
            pstmt.setString(2, avatar);
            pstmt.setString(3, username);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("[UserDAO] Lỗi cập nhật hồ sơ: " + e.getMessage());
            return false;
        }
    }

    // 6. Lấy thông tin 1 người dùng theo username
    public static Map<String, String> getUserByUsername(String username) {
        String sql = "SELECT username, full_name, avatar, status FROM users WHERE username = ?";
        try (Connection conn = DatabaseManager.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                Map<String, String> user = new HashMap<>();
                user.put("username", rs.getString("username"));
                user.put("fullName", rs.getString("full_name"));
                user.put("avatar", rs.getString("avatar"));
                user.put("status", rs.getString("status"));
                return user;
            }
        } catch (SQLException e) {
            System.err.println("[UserDAO] Lỗi tìm user: " + e.getMessage());
        }
        return null;
    }
}
