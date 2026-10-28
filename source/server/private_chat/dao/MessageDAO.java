package private_chat.dao;

import database.DatabaseManager;
import private_chat.model.Conversation;
import private_chat.model.Message;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class MessageDAO {

    /**
     * Lưu một tin nhắn mới vào đoạn chat.
     * Tự động cập nhật thời gian hoạt động (updated_at) của đoạn chat.
     *
     * @param conversationId ID của đoạn chat
     * @param senderUsername Tên người gửi
     * @param content        Nội dung tin nhắn
     * @return Đối tượng Message đầy đủ (kèm id và sentAt)
     */
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

                        // Cập nhật thời gian hoạt động của conversation
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

    /**
     * Lấy thông tin một tin nhắn theo ID.
     */
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

    /**
     * Lấy danh sách tin nhắn của một đoạn chat theo conversationId.
     * Lấy N tin mới nhất và sắp xếp theo thứ tự thời gian tăng dần (cũ -> mới).
     *
     * @param conversationId ID đoạn chat
     * @param limit          Số lượng tin nhắn tối đa
     * @return Danh sách tin nhắn
     */
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

    /**
     * Tiện ích lấy lịch sử chat giữa 2 người dùng (tự tìm hoặc tạo conversation tương ứng).
     */
    public static List<Message> getMessagesBetweenUsers(String userA, String userB, int limit) {
        Conversation conv = ConversationDAO.getOrCreateConversation(userA, userB);
        if (conv == null) {
            return new ArrayList<>();
        }
        return getMessagesByConversationId(conv.getId(), limit);
    }

    /**
     * Đánh dấu tất cả tin nhắn trong đoạn chat mà đối phương gửi cho tôi là ĐÃ ĐỌC.
     *
     * @param conversationId ID đoạn chat
     * @param readerUsername Người đang đọc tin (chỉ đánh dấu tin do người khác gửi)
     */
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

    /**
     * Đếm số lượng tin nhắn chưa đọc trong đoạn chat mà người khác gửi cho tôi.
     */
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
