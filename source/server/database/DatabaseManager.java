package database;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class DatabaseManager {
    // Test chạy với -Dchatnest.db.dir=... để không đụng vào CSDL thật
    private static final String DB_DIR = System.getProperty("chatnest.db.dir", "source/server/data");
    private static final String DB_URL = "jdbc:sqlite:" + DB_DIR + "/messaging_app.db";

    // Mỗi lần gọi mở một kết nối mới, thư mục data chưa có thì tạo
    public static Connection getConnection() throws SQLException {
        File dir = new File(DB_DIR);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return DriverManager.getConnection(DB_URL);
    }

    // Gọi khi Server khởi động, dùng IF NOT EXISTS nên chạy lại nhiều lần vẫn được
    public static void initDatabase() {
        // users
        String createUsersTable = "CREATE TABLE IF NOT EXISTS users ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "username TEXT UNIQUE NOT NULL COLLATE NOCASE, "
                + "password TEXT NOT NULL, "
                + "full_name TEXT NOT NULL, "
                + "avatar TEXT DEFAULT '', "
                + "status TEXT DEFAULT 'OFFLINE', "
                + "created_at DATETIME DEFAULT CURRENT_TIMESTAMP"
                + ");";

        // conversations: mỗi cặp người dùng một dòng
        String createConversationsTable = "CREATE TABLE IF NOT EXISTS conversations ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "user1_username TEXT NOT NULL COLLATE NOCASE, "
                + "user2_username TEXT NOT NULL COLLATE NOCASE, "
                + "created_at DATETIME DEFAULT CURRENT_TIMESTAMP, "
                + "updated_at DATETIME DEFAULT CURRENT_TIMESTAMP, "
                + "UNIQUE(user1_username, user2_username)"
                + ");";

        // messages: tin nhắn riêng, theo conversation_id
        String createMessagesTable = "CREATE TABLE IF NOT EXISTS messages ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "conversation_id INTEGER NOT NULL, "
                + "sender_username TEXT NOT NULL COLLATE NOCASE, "
                + "content TEXT NOT NULL, "
                + "sent_at DATETIME DEFAULT CURRENT_TIMESTAMP, "
                + "is_read INTEGER DEFAULT 0, "
                + "FOREIGN KEY (conversation_id) REFERENCES conversations(id) ON DELETE CASCADE"
                + ");";

        // file_attachments: thông tin tệp, nội dung nằm trong thư mục uploads
        String createFileAttachmentsTable = "CREATE TABLE IF NOT EXISTS file_attachments ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "conversation_id INTEGER NOT NULL, "
                + "message_id INTEGER, "
                + "sender_username TEXT NOT NULL COLLATE NOCASE, "
                + "receiver_username TEXT NOT NULL COLLATE NOCASE, "
                + "file_name TEXT NOT NULL, "
                + "file_type TEXT NOT NULL, " // 'IMAGE', 'AUDIO', 'VIDEO', 'FILE'
                + "file_size INTEGER NOT NULL, "
                + "stored_path TEXT NOT NULL, "
                + "created_at DATETIME DEFAULT CURRENT_TIMESTAMP, "
                + "FOREIGN KEY (conversation_id) REFERENCES conversations(id) ON DELETE CASCADE"
                + ");";

        // Các bảng của chat nhóm
        String createGroupsTable = "CREATE TABLE IF NOT EXISTS chat_groups ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "name TEXT NOT NULL, "
                + "created_at DATETIME DEFAULT CURRENT_TIMESTAMP"
                + ");";

        // role là LEADER (trưởng nhóm) hoặc MEMBER
        String createGroupMembersTable = "CREATE TABLE IF NOT EXISTS group_members ("
                + "group_id INTEGER NOT NULL, "
                + "username TEXT NOT NULL COLLATE NOCASE, "
                + "role TEXT NOT NULL DEFAULT 'MEMBER', "
                + "joined_at DATETIME DEFAULT CURRENT_TIMESTAMP, "
                + "PRIMARY KEY (group_id, username)"
                + ");";

        String createGroupMessagesTable = "CREATE TABLE IF NOT EXISTS group_messages ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "group_id INTEGER NOT NULL, "
                + "sender_username TEXT NOT NULL COLLATE NOCASE, "
                + "content TEXT NOT NULL, "
                + "sent_at DATETIME DEFAULT CURRENT_TIMESTAMP"
                + ");";

        String createGroupMembersIndex = "CREATE INDEX IF NOT EXISTS idx_group_members_user "
                + "ON group_members(username);";

        String createGroupMessagesIndex = "CREATE INDEX IF NOT EXISTS idx_group_messages_group "
                + "ON group_messages(group_id, id);";

        // Chỉ mục cho các cột hay dùng trong WHERE
        String createConvIndex = "CREATE INDEX IF NOT EXISTS idx_conv_users "
                + "ON conversations(user1_username, user2_username);";

        String createMessagesIndex = "CREATE INDEX IF NOT EXISTS idx_messages_conv_time "
                + "ON messages(conversation_id, sent_at);";

        String createFileIndex = "CREATE INDEX IF NOT EXISTS idx_files_conv "
                + "ON file_attachments(conversation_id, created_at);";

        try (Connection conn = getConnection();
                Statement stmt = conn.createStatement()) {

            // WAL để việc đọc không bị chặn khi đang ghi
            stmt.execute("PRAGMA journal_mode=WAL;");

            // Tạo các bảng nếu chưa có
            stmt.execute(createUsersTable);
            stmt.execute(createConversationsTable);
            stmt.execute(createMessagesTable);
            stmt.execute(createFileAttachmentsTable);
            stmt.execute(createGroupsTable);
            stmt.execute(createGroupMembersTable);
            stmt.execute(createGroupMessagesTable);

            // Tạo chỉ mục
            stmt.execute(createConvIndex);
            stmt.execute(createMessagesIndex);
            stmt.execute(createFileIndex);
            stmt.execute(createGroupMembersIndex);
            stmt.execute(createGroupMessagesIndex);

            // Tạo sẵn thư mục uploads
            File uploadDir = new File(DB_DIR + "/uploads");
            if (!uploadDir.exists()) {
                uploadDir.mkdirs();
            }

            // Lần trước Server có thể bị tắt ngang, nên đưa hết về OFFLINE
            stmt.executeUpdate("UPDATE users SET status = 'OFFLINE';");

            System.out.println("[DATABASE] Kết nối và khởi tạo CSDL SQLite thành công (users, conversations, messages, file_attachments, chat_groups, group_members, group_messages)!");
        } catch (SQLException e) {
            System.err.println("[DATABASE] Lỗi khởi tạo CSDL: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
