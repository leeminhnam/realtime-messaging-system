package file_notification.dao;

import database.DatabaseManager;
import file_notification.model.FileAttachment;

import java.sql.*;

public class FileAttachmentDAO {

    /**
     * Lưu thông tin một tệp đính kèm vào CSDL.
     */
    public static FileAttachment saveFileAttachment(int conversationId, int messageId, String sender, String receiver,
                                                    String fileName, String fileType, long fileSize, String storedPath) {
        String insertSQL = "INSERT INTO file_attachments(conversation_id, message_id, sender_username, "
                + "receiver_username, file_name, file_type, file_size, stored_path, created_at) "
                + "VALUES(?, ?, ?, ?, ?, ?, ?, ?, datetime('now', 'localtime'))";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(insertSQL, Statement.RETURN_GENERATED_KEYS)) {

            pstmt.setInt(1, conversationId);
            pstmt.setInt(2, messageId);
            pstmt.setString(3, sender);
            pstmt.setString(4, receiver);
            pstmt.setString(5, fileName);
            pstmt.setString(6, fileType);
            pstmt.setLong(7, fileSize);
            pstmt.setString(8, storedPath);

            int affected = pstmt.executeUpdate();
            if (affected > 0) {
                try (ResultSet rs = pstmt.getGeneratedKeys()) {
                    if (rs.next()) {
                        int generatedId = rs.getInt(1);
                        return getFileAttachmentById(generatedId);
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("[FileAttachmentDAO] Lỗi lưu thông tin tệp: " + e.getMessage());
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Lấy tệp đính kèm theo ID.
     */
    public static FileAttachment getFileAttachmentById(int id) {
        String sql = "SELECT id, conversation_id, message_id, sender_username, receiver_username, "
                + "file_name, file_type, file_size, stored_path, created_at "
                + "FROM file_attachments WHERE id = ?";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, id);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return new FileAttachment(
                            rs.getInt("id"),
                            rs.getInt("conversation_id"),
                            rs.getInt("message_id"),
                            rs.getString("sender_username"),
                            rs.getString("receiver_username"),
                            rs.getString("file_name"),
                            rs.getString("file_type"),
                            rs.getLong("file_size"),
                            rs.getString("stored_path"),
                            rs.getString("created_at")
                    );
                }
            }
        } catch (SQLException e) {
            System.err.println("[FileAttachmentDAO] Lỗi lấy tệp theo ID: " + e.getMessage());
        }
        return null;
    }

    /**
     * Cập nhật messageId cho tệp đính kèm sau khi Message đã được tạo.
     */
    public static void updateMessageId(int fileId, int messageId) {
        String sql = "UPDATE file_attachments SET message_id = ? WHERE id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, messageId);
            pstmt.setInt(2, fileId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[FileAttachmentDAO] Lỗi cập nhật messageId: " + e.getMessage());
        }
    }
}
