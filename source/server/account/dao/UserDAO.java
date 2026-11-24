package account.dao;

import account.model.User;
import database.DatabaseManager;
import org.mindrot.jbcrypt.BCrypt;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class UserDAO {

    // Mật khẩu được băm bằng BCrypt trước khi lưu
    public static boolean register(String username, String password, String fullName) {
        String sql = "INSERT INTO users(username, password, full_name, status) VALUES(?, ?, ?, 'OFFLINE')";

        // Băm mật khẩu
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

    // Xác thực đăng nhập
    public static Map<String, String> login(String username, String password) {
        String sql = "SELECT * FROM users WHERE username = ?";

        try (Connection conn = DatabaseManager.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                String storedHash = rs.getString("password");
                // So mật khẩu nhập vào với mã băm trong CSDL
                if (BCrypt.checkpw(password, storedHash)) {
                    // Chuyển trạng thái sang ONLINE
                    updateStatus(username, "ONLINE");

                    User user = new User(rs.getString("username"), rs.getString("full_name"),
                            rs.getString("avatar"), "ONLINE");
                    return user.toMap();
                }
            }
        } catch (SQLException e) {
            System.err.println("[UserDAO] Lỗi đăng nhập: " + e.getMessage());
        }
        return null; // sai tên đăng nhập hoặc mật khẩu
    }

    // Cập nhật trạng thái ONLINE/OFFLINE
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

    // Toàn bộ người dùng, dùng để dựng danh bạ
    public static List<Map<String, String>> getAllUsers() {
        List<Map<String, String>> userList = new ArrayList<>();
        String sql = "SELECT username, full_name, avatar, status FROM users";

        try (Connection conn = DatabaseManager.getConnection();
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                User user = new User(rs.getString("username"), rs.getString("full_name"),
                        rs.getString("avatar"), rs.getString("status"));
                userList.add(user.toMap());
            }
        } catch (SQLException e) {
            System.err.println("[UserDAO] Lỗi lấy danh sách user: " + e.getMessage());
        }
        return userList;
    }

    // Cập nhật hồ sơ cá nhân
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

    // Lấy thông tin một người dùng theo username
    public static Map<String, String> getUserByUsername(String username) {
        String sql = "SELECT username, full_name, avatar, status FROM users WHERE username = ?";
        try (Connection conn = DatabaseManager.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                User user = new User(rs.getString("username"), rs.getString("full_name"),
                        rs.getString("avatar"), rs.getString("status"));
                return user.toMap();
            }
        } catch (SQLException e) {
            System.err.println("[UserDAO] Lỗi tìm user: " + e.getMessage());
        }
        return null;
    }
}
