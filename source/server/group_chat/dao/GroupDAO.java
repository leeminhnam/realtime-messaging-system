package group_chat.dao;

import database.DatabaseManager;
import file_notification.dao.FileAttachmentDAO;
import group_chat.model.ChatGroup;
import group_chat.model.GroupMember;

import java.io.File;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class GroupDAO {
    public static final String LEADER = "LEADER";
    public static final String MEMBER = "MEMBER";

    // Câu SELECT dùng chung
    private static final String SELECT_GROUP = "SELECT g.id, g.name, g.created_at, "
            + "(SELECT username FROM group_members WHERE group_id = g.id AND role = 'LEADER') AS leader, "
            + "(SELECT COUNT(*) FROM group_members WHERE group_id = g.id) AS member_count "
            + "FROM chat_groups g ";

    public static int createGroup(String name, String leader, List<String> members) {
        String sql = "INSERT INTO chat_groups(name, created_at) VALUES(?, datetime('now', 'localtime'))";

        try (Connection conn = DatabaseManager.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            pstmt.setString(1, name);
            pstmt.executeUpdate();

            try (ResultSet rs = pstmt.getGeneratedKeys()) {
                if (rs.next()) {
                    int groupId = rs.getInt(1);
                    addMember(groupId, leader, LEADER);
                    for (String member : members) {
                        addMember(groupId, member, MEMBER);
                    }
                    return groupId;
                }
            }
        } catch (SQLException e) {
            System.err.println("[GroupDAO] Lỗi tạo nhóm: " + e.getMessage());
        }
        return -1;
    }

    public static boolean addMember(int groupId, String username, String role) {
        String sql = "INSERT INTO group_members(group_id, username, role, joined_at) "
                + "VALUES(?, ?, ?, datetime('now', 'localtime'))";

        try (Connection conn = DatabaseManager.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, groupId);
            pstmt.setString(2, username);
            pstmt.setString(3, role);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("[GroupDAO] Lỗi thêm thành viên: " + e.getMessage());
            return false;
        }
    }

    public static boolean removeMember(int groupId, String username) {
        String sql = "DELETE FROM group_members WHERE group_id = ? AND username = ?";

        try (Connection conn = DatabaseManager.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, groupId);
            pstmt.setString(2, username);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("[GroupDAO] Lỗi xóa thành viên: " + e.getMessage());
            return false;
        }
    }

    // Đổi vai trò thành viên (LEADER/MEMBER)
    public static boolean setRole(int groupId, String username, String role) {
        String sql = "UPDATE group_members SET role = ? WHERE group_id = ? AND username = ?";

        try (Connection conn = DatabaseManager.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, role);
            pstmt.setInt(2, groupId);
            pstmt.setString(3, username);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("[GroupDAO] Lỗi đổi vai trò: " + e.getMessage());
            return false;
        }
    }

    public static boolean transferLeader(int groupId, String oldLeader, String newLeader) {
        return setRole(groupId, oldLeader, MEMBER) && setRole(groupId, newLeader, LEADER);
    }

    // Xóa nhóm 
    public static void deleteGroup(int groupId) {
        List<String> storedFiles = FileAttachmentDAO.getStoredPathsOfGroup(groupId);
        String[] sqls = {
                "DELETE FROM file_attachments WHERE group_id = ?",
                "DELETE FROM group_messages WHERE group_id = ?",
                "DELETE FROM group_members WHERE group_id = ?",
                "DELETE FROM chat_groups WHERE id = ?"
        };

        try (Connection conn = DatabaseManager.getConnection()) {
            for (String sql : sqls) {
                try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                    pstmt.setInt(1, groupId);
                    pstmt.executeUpdate();
                }
            }
        } catch (SQLException e) {
            System.err.println("[GroupDAO] Lỗi xóa nhóm: " + e.getMessage());
            return;
        }

        for (String path : storedFiles) {
            new File(path).delete();
        }
    }

    public static String getRole(int groupId, String username) {
        String sql = "SELECT role FROM group_members WHERE group_id = ? AND username = ?";

        try (Connection conn = DatabaseManager.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, groupId);
            pstmt.setString(2, username);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("role");
                }
            }
        } catch (SQLException e) {
            System.err.println("[GroupDAO] Lỗi lấy vai trò: " + e.getMessage());
        }
        return null;
    }

    public static ChatGroup getGroupById(int groupId) {
        try (Connection conn = DatabaseManager.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(SELECT_GROUP + "WHERE g.id = ?")) {

            pstmt.setInt(1, groupId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return readGroup(rs);
                }
            }
        } catch (SQLException e) {
            System.err.println("[GroupDAO] Lỗi lấy nhóm: " + e.getMessage());
        }
        return null;
    }

    // Các nhóm mà người dùng đang tham gia
    public static List<ChatGroup> getGroupsOfUser(String username) {
        List<ChatGroup> list = new ArrayList<>();
        String sql = SELECT_GROUP + "WHERE g.id IN (SELECT group_id FROM group_members WHERE username = ?) "
                + "ORDER BY g.id DESC";

        try (Connection conn = DatabaseManager.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, username);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    list.add(readGroup(rs));
                }
            }
        } catch (SQLException e) {
            System.err.println("[GroupDAO] Lỗi lấy danh sách nhóm: " + e.getMessage());
        }
        return list;
    }

    public static List<GroupMember> getMembers(int groupId) {
        List<GroupMember> list = new ArrayList<>();
        String sql = "SELECT m.username, u.full_name, u.status, m.role "
                + "FROM group_members m LEFT JOIN users u ON u.username = m.username "
                + "WHERE m.group_id = ? "
                + "ORDER BY CASE m.role WHEN 'LEADER' THEN 0 ELSE 1 END, m.joined_at";

        try (Connection conn = DatabaseManager.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, groupId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    String username = rs.getString("username");
                    String fullName = rs.getString("full_name");
                    String status = rs.getString("status");
                    list.add(new GroupMember(
                            username,
                            fullName != null ? fullName : username,
                            rs.getString("role"),
                            status != null ? status : "OFFLINE"));
                }
            }
        } catch (SQLException e) {
            System.err.println("[GroupDAO] Lỗi lấy thành viên: " + e.getMessage());
        }
        return list;
    }

    private static ChatGroup readGroup(ResultSet rs) throws SQLException {
        return new ChatGroup(
                rs.getInt("id"),
                rs.getString("name"),
                rs.getString("leader"),
                rs.getInt("member_count"),
                rs.getString("created_at"));
    }
}
