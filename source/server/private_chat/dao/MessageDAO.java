package private_chat.dao;

import database.DatabaseManager;
import private_chat.model.Conversation;
import private_chat.model.Message;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class MessageDAO {

    // Lưu tin và cập nhật updated_at của hội thoại
    public static Message saveMessage(int conversationId, String senderUsername, String content) {
        String insertSQL = "INSERT INTO messages(conversation_id, sender_username, content, sent_at) "
                + "VALUES(?, ?, ?, datetime('now', 'localtime'))";

        try (Connection conn = DatabaseManager.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(insertSQL, Statement.RETURN_GENERATED_KEYS)) {

            pstmt.setInt(1, conversationId);
            pstmt.setString(2, senderUsername);
            pstmt.setString(3, content);

            int affected = pstmt.executeUpdate();
            if (affected > 0) {
                try (ResultSet rs = pstmt.getGeneratedKeys()) {
                    if (rs.next()) {
                        int generatedId = rs.getInt(1);

                        // Cập nhật updated_at của conversation
                        ConversationDAO.updateLastActivity(conversationId);

                        return getMessageById(generatedId);
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("[MessageDAO] Lỗi lưu tin nhắn: " + e.getMessage());
            e.printStackTrace();
        }
        return null;
    }

    // Lấy tin nhắn theo id
    public static Message getMessageById(int id) {
        String sql = "SELECT id, conversation_id, sender_username, content, sent_at, is_read "
                + "FROM messages WHERE id = ?";

        try (Connection conn = DatabaseManager.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, id);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return new Message(
                            rs.getInt("id"),
                            rs.getInt("conversation_id"),
                            rs.getString("sender_username"),
                            rs.getString("content"),
                            rs.getString("sent_at"),
                            rs.getInt("is_read") == 1);
                }
            }
        } catch (SQLException e) {
            System.err.println("[MessageDAO] Lỗi lấy tin nhắn theo ID: " + e.getMessage());
        }
        return null;
    }

    // Lấy N tin mới nhất, trả về theo thứ tự cũ đến mới
    public static List<Message> getMessagesByConversationId(int conversationId, int limit) {
        List<Message> list = new ArrayList<>();

        String sql = "SELECT * FROM ("
                + "  SELECT id, conversation_id, sender_username, content, sent_at, is_read "
                + "  FROM messages "
                + "  WHERE conversation_id = ? "
                + "  ORDER BY sent_at DESC, id DESC "
                + "  LIMIT ?"
                + ") sub ORDER BY sent_at ASC, id ASC";

        try (Connection conn = DatabaseManager.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, conversationId);
            pstmt.setInt(2, limit);

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    list.add(new Message(
                            rs.getInt("id"),
                            rs.getInt("conversation_id"),
                            rs.getString("sender_username"),
                            rs.getString("content"),
                            rs.getString("sent_at"),
                            rs.getInt("is_read") == 1));
                }
            }
        } catch (SQLException e) {
            System.err.println("[MessageDAO] Lỗi lấy tin nhắn theo conversationId: " + e.getMessage());
        }
        return list;
    }

    // Lịch sử giữa hai người, chưa có hội thoại thì tạo
    public static List<Message> getMessagesBetweenUsers(String userA, String userB, int limit) {
        Conversation conv = ConversationDAO.getOrCreateConversation(userA, userB);
        if (conv == null) {
            return new ArrayList<>();
        }
        return getMessagesByConversationId(conv.getId(), limit);
    }

    // Chỉ đánh dấu tin do người kia gửi
    public static int markMessagesAsRead(int conversationId, String readerUsername) {
        String sql = "UPDATE messages SET is_read = 1 "
                + "WHERE conversation_id = ? AND sender_username != ? AND is_read = 0";

        try (Connection conn = DatabaseManager.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, conversationId);
            pstmt.setString(2, readerUsername);
            return pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[MessageDAO] Lỗi đánh dấu tin nhắn đã đọc: " + e.getMessage());
            return 0;
        }
    }

    // Đếm tin chưa đọc do người kia gửi
    public static int getUnreadCount(int conversationId, String readerUsername) {
        String sql = "SELECT COUNT(*) FROM messages "
                + "WHERE conversation_id = ? AND sender_username != ? AND is_read = 0";

        try (Connection conn = DatabaseManager.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, conversationId);
            pstmt.setString(2, readerUsername);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            System.err.println("[MessageDAO] Lỗi đếm tin nhắn chưa đọc: " + e.getMessage());
        }
        return 0;
    }
}
