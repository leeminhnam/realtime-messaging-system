package group_chat.dao;

import database.DatabaseManager;
import group_chat.model.GroupMessage;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class GroupMessageDAO {

    // Câu SELECT dùng chung: lấy tin nhắn kèm họ tên người gửi
    private static final String SELECT_MESSAGE = "SELECT m.id, m.group_id, m.sender_username, u.full_name, "
            + "m.content, m.sent_at FROM group_messages m "
            + "LEFT JOIN users u ON u.username = m.sender_username ";

    /**
     * Lưu một tin nhắn nhóm mới vào CSDL.
     *
     * @return Tin nhắn vừa lưu (có id và sentAt), null nếu lỗi
     */
    public static GroupMessage saveMessage(int groupId, String senderUsername, String content) {
        String sql = "INSERT INTO group_messages(group_id, sender_username, content, sent_at) "
                + "VALUES(?, ?, ?, datetime('now', 'localtime'))";

        try (Connection conn = DatabaseManager.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            pstmt.setInt(1, groupId);
            pstmt.setString(2, senderUsername);
            pstmt.setString(3, content);
            pstmt.executeUpdate();

            try (ResultSet rs = pstmt.getGeneratedKeys()) {
                if (rs.next()) {
                    return getMessageById(rs.getInt(1));
                }
            }
        } catch (SQLException e) {
            System.err.println("[GroupMessageDAO] Lỗi lưu tin nhắn nhóm: " + e.getMessage());
        }
        return null;
    }

    /**
     * Lấy một tin nhắn nhóm theo id.
     */
    public static GroupMessage getMessageById(int id) {
        try (Connection conn = DatabaseManager.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(SELECT_MESSAGE + "WHERE m.id = ?")) {

            pstmt.setInt(1, id);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return readMessage(rs);
                }
            }
        } catch (SQLException e) {
            System.err.println("[GroupMessageDAO] Lỗi lấy tin nhắn nhóm: " + e.getMessage());
        }
        return null;
    }

    /**
     * Lấy lịch sử N tin nhắn mới nhất của nhóm, sắp xếp từ cũ đến mới.
     */
    public static List<GroupMessage> getHistory(int groupId, int limit) {
        List<GroupMessage> list = new ArrayList<>();
        String sql = "SELECT * FROM (" + SELECT_MESSAGE
                + "WHERE m.group_id = ? ORDER BY m.id DESC LIMIT ?) ORDER BY id ASC";

        try (Connection conn = DatabaseManager.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, groupId);
            pstmt.setInt(2, limit);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    list.add(readMessage(rs));
                }
            }
        } catch (SQLException e) {
            System.err.println("[GroupMessageDAO] Lỗi lấy lịch sử nhóm: " + e.getMessage());
        }
        return list;
    }

    // Đọc một dòng kết quả thành đối tượng GroupMessage
    private static GroupMessage readMessage(ResultSet rs) throws SQLException {
        String sender = rs.getString("sender_username");
        String fullName = rs.getString("full_name");
        return new GroupMessage(
                rs.getInt("id"),
                rs.getInt("group_id"),
                sender,
                fullName != null ? fullName : sender,
                rs.getString("content"),
                rs.getString("sent_at"));
    }
}
