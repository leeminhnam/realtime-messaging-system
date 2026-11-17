package file_notification.dao;

import database.DatabaseManager;
import file_notification.model.FileAttachment;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class FileAttachmentDAO {

    // Lưu thông tin tệp đính kèm vào CSDL (chat 1-1)
    public static FileAttachment saveFileAttachment(int conversationId, int messageId, String sender, String receiver,
                                                    String fileName, String fileType, long fileSize, String storedPath) {
        return insert(conversationId, 0, messageId, sender, receiver, fileName, fileType, fileSize, storedPath);
    }

    // Lưu tệp gửi trong nhóm: không có người nhận cụ thể nên receiver để trống
    public static FileAttachment saveGroupFileAttachment(int groupId, String sender, String fileName, String fileType,
                                                         long fileSize, String storedPath) {
        return insert(0, groupId, 0, sender, "", fileName, fileType, fileSize, storedPath);
    }

    // Đường dẫn trên đĩa của mọi tệp trong nhóm, dùng khi xóa nhóm
    public static List<String> getStoredPathsOfGroup(int groupId) {
        List<String> paths = new ArrayList<>();
        String sql = "SELECT stored_path FROM file_attachments WHERE group_id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, groupId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    paths.add(rs.getString("stored_path"));
                }
            }
        } catch (SQLException e) {
            System.err.println("[FileAttachmentDAO] Lỗi lấy tệp của nhóm: " + e.getMessage());
        }
        return paths;
    }

    private static FileAttachment insert(int conversationId, int groupId, int messageId, String sender, String receiver,
                                         String fileName, String fileType, long fileSize, String storedPath) {
        String insertSQL = "INSERT INTO file_attachments(conversation_id, group_id, message_id, sender_username, "
                + "receiver_username, file_name, file_type, file_size, stored_path, created_at) "
                + "VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, datetime('now', 'localtime'))";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(insertSQL, Statement.RETURN_GENERATED_KEYS)) {

            pstmt.setInt(1, conversationId);
            pstmt.setInt(2, groupId);
            pstmt.setInt(3, messageId);
            pstmt.setString(4, sender);
            pstmt.setString(5, receiver);
            pstmt.setString(6, fileName);
            pstmt.setString(7, fileType);
            pstmt.setLong(8, fileSize);
            pstmt.setString(9, storedPath);

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

    // Lấy tệp đính kèm theo id
    public static FileAttachment getFileAttachmentById(int id) {
        String sql = "SELECT id, conversation_id, message_id, sender_username, receiver_username, "
                + "file_name, file_type, file_size, stored_path, created_at, group_id "
                + "FROM file_attachments WHERE id = ?";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, id);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    FileAttachment f = new FileAttachment(
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
                    f.setGroupId(rs.getInt("group_id"));
                    return f;
                }
            }
        } catch (SQLException e) {
            System.err.println("[FileAttachmentDAO] Lỗi lấy tệp theo ID: " + e.getMessage());
        }
        return null;
    }

    // Tệp được lưu trước tin nhắn nên phải gắn messageId sau
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
