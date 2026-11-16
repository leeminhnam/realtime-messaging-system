package private_chat.dao;

import database.DatabaseManager;
import private_chat.model.Conversation;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class ConversationDAO {

    // Mỗi cặp người dùng chỉ có một hội thoại: sắp xếp hai username trước khi tìm
    public static Conversation getOrCreateConversation(String userA, String userB) {
        if (userA == null || userB == null || userA.trim().isEmpty() || userB.trim().isEmpty()
                || userA.trim().equalsIgnoreCase(userB.trim())) {
            System.err.println("[ConversationDAO] userA hoặc userB không hợp lệ: userA=" + userA + ", userB=" + userB);
            return null;
        }

        userA = userA.trim();
        userB = userB.trim();

        // user1 luôn đứng trước user2 theo alphabet
        String u1 = userA.compareToIgnoreCase(userB) <= 0 ? userA : userB;
        String u2 = userA.compareToIgnoreCase(userB) <= 0 ? userB : userA;

        // Xem đoạn chat đã có chưa
        String findSQL = "SELECT id, user1_username, user2_username, created_at, updated_at "
                + "FROM conversations WHERE (user1_username = ? AND user2_username = ?) "
                + "   OR (user1_username = ? AND user2_username = ?)";

        try (Connection conn = DatabaseManager.getConnection()) {
            try (PreparedStatement pstmt = conn.prepareStatement(findSQL)) {
                pstmt.setString(1, u1);
                pstmt.setString(2, u2);
                pstmt.setString(3, u2);
                pstmt.setString(4, u1);

                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        return new Conversation(
                                rs.getInt("id"),
                                rs.getString("user1_username"),
                                rs.getString("user2_username"),
                                rs.getString("created_at"),
                                rs.getString("updated_at"));
                    }
                }
            }

            // Chưa có thì tạo
            String insertSQL = "INSERT INTO conversations(user1_username, user2_username, created_at, updated_at) "
                    + "VALUES(?, ?, datetime('now', 'localtime'), datetime('now', 'localtime'))";

            try (PreparedStatement pstmt = conn.prepareStatement(insertSQL, Statement.RETURN_GENERATED_KEYS)) {
                pstmt.setString(1, u1);
                pstmt.setString(2, u2);
                int affected = pstmt.executeUpdate();

                if (affected > 0) {
                    try (ResultSet rs = pstmt.getGeneratedKeys()) {
                        if (rs.next()) {
                            int newId = rs.getInt(1);
                            Conversation conv = getConversationById(newId);
                            if (conv != null) {
                                return conv;
                            }
                        }
                    }
                }
            } catch (SQLException insertEx) {
                System.err.println("[ConversationDAO] Insert đoạn chat gặp lỗi, thử tìm lại: " + insertEx.getMessage());
            }

            // Có thể luồng khác vừa tạo xong, tìm lại lần nữa
            try (PreparedStatement pstmt = conn.prepareStatement(findSQL)) {
                pstmt.setString(1, u1);
                pstmt.setString(2, u2);
                pstmt.setString(3, u2);
                pstmt.setString(4, u1);

                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        return new Conversation(
                                rs.getInt("id"),
                                rs.getString("user1_username"),
                                rs.getString("user2_username"),
                                rs.getString("created_at"),
                                rs.getString("updated_at"));
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("[ConversationDAO] Lỗi truy vấn/tạo đoạn chat: " + e.getMessage());
            e.printStackTrace();
        }

        return null;
    }

    // Lấy đoạn chat theo id
    public static Conversation getConversationById(int id) {
        String sql = "SELECT id, user1_username, user2_username, created_at, updated_at "
                + "FROM conversations WHERE id = ?";

        try (Connection conn = DatabaseManager.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, id);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return new Conversation(
                            rs.getInt("id"),
                            rs.getString("user1_username"),
                            rs.getString("user2_username"),
                            rs.getString("created_at"),
                            rs.getString("updated_at"));
                }
            }
        } catch (SQLException e) {
            System.err.println("[ConversationDAO] Lỗi tìm đoạn chat theo ID: " + e.getMessage());
        }
        return null;
    }

    // Gọi mỗi khi có tin nhắn mới
    public static void updateLastActivity(int conversationId) {
        String sql = "UPDATE conversations SET updated_at = datetime('now', 'localtime') WHERE id = ?";
        try (Connection conn = DatabaseManager.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, conversationId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[ConversationDAO] Lỗi cập nhật thời gian hoạt động: " + e.getMessage());
        }
    }

    // Hội thoại mới nhất lên đầu
    public static List<Conversation> getUserConversations(String username) {
        List<Conversation> list = new ArrayList<>();
        String sql = "SELECT id, user1_username, user2_username, created_at, updated_at "
                + "FROM conversations WHERE user1_username = ? OR user2_username = ? "
                + "ORDER BY updated_at DESC";

        try (Connection conn = DatabaseManager.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, username);
            pstmt.setString(2, username);

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    list.add(new Conversation(
                            rs.getInt("id"),
                            rs.getString("user1_username"),
                            rs.getString("user2_username"),
                            rs.getString("created_at"),
                            rs.getString("updated_at")));
                }
            }
        } catch (SQLException e) {
            System.err.println("[ConversationDAO] Lỗi lấy danh sách đoạn chat của user: " + e.getMessage());
        }
        return list;
    }
}
