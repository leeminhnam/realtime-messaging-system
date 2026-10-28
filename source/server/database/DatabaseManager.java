package database;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class DatabaseManager {
    private static final String DB_DIR = "source/server/data";
    private static final String DB_URL = "jdbc:sqlite:" + DB_DIR + "/messaging_app.db";

    /**
     * Cung cấp kết nối CSDL SQLite dùng chung cho toàn bộ Server và các lớp DAO.
     * Tự động kiểm tra và tạo thư mục chứa nếu chưa tồn tại.
     */
    public static Connection getConnection() throws SQLException {
        File dir = new File(DB_DIR);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return DriverManager.getConnection(DB_URL);
    }

    /**
     * Khởi tạo cấu trúc bảng CSDL SQLite khi Server khởi động:
     * - users: Lưu tài khoản người dùng
     * - conversations: Lưu thông tin đoạn chat (gồm 2 người tham gia)
     * - messages: Lưu tất cả tin nhắn thuộc về từng đoạn chat
     */
    public static void initDatabase() {
        // 1. Bảng người dùng
        String createUsersTable = "CREATE TABLE IF NOT EXISTS users ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "username TEXT UNIQUE NOT NULL COLLATE NOCASE, "
                + "password TEXT NOT NULL, "
                + "full_name TEXT NOT NULL, "
                + "avatar TEXT DEFAULT '', "
                + "status TEXT DEFAULT 'OFFLINE', "
                + "created_at DATETIME DEFAULT CURRENT_TIMESTAMP"
                + ");";

        // 2. Bảng đoạn chat (Conversation) - lưu 2 người trong đoạn chat
        String createConversationsTable = "CREATE TABLE IF NOT EXISTS conversations ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "user1_username TEXT NOT NULL COLLATE NOCASE, "
                + "user2_username TEXT NOT NULL COLLATE NOCASE, "
                + "created_at DATETIME DEFAULT CURRENT_TIMESTAMP, "
                + "updated_at DATETIME DEFAULT CURRENT_TIMESTAMP, "
                + "UNIQUE(user1_username, user2_username)"
                + ");";

        // 3. Bảng tin nhắn (Message) - lưu tất cả tin nhắn theo conversation_id
        String createMessagesTable = "CREATE TABLE IF NOT EXISTS messages ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "conversation_id INTEGER NOT NULL, "
                + "sender_username TEXT NOT NULL COLLATE NOCASE, "
                + "content TEXT NOT NULL, "
                + "sent_at DATETIME DEFAULT CURRENT_TIMESTAMP, "
                + "is_read INTEGER DEFAULT 0, "
                + "FOREIGN KEY (conversation_id) REFERENCES conversations(id) ON DELETE CASCADE"
                + ");";

        // 4. Các Index để tối ưu tốc độ truy vấn
        String createConvIndex = "CREATE INDEX IF NOT EXISTS idx_conv_users "
                + "ON conversations(user1_username, user2_username);";

        String createMessagesIndex = "CREATE INDEX IF NOT EXISTS idx_messages_conv_time "
                + "ON messages(conversation_id, sent_at);";

        try (Connection conn = getConnection();
                Statement stmt = conn.createStatement()) {

            // Bật chế độ Write-Ahead Logging (WAL) để tăng tốc độ đọc/ghi đồng thời
            stmt.execute("PRAGMA journal_mode=WAL;");

            // Tạo các bảng nếu chưa có
            stmt.execute(createUsersTable);
            stmt.execute(createConversationsTable);
            stmt.execute(createMessagesTable);

            // Tạo các chỉ mục tìm kiếm
            stmt.execute(createConvIndex);
            stmt.execute(createMessagesIndex);

            // Reset tất cả tài khoản về OFFLINE khi server khởi động lại
            stmt.executeUpdate("UPDATE users SET status = 'OFFLINE';");

            System.out.println("[DATABASE] Kết nối và khởi tạo CSDL SQLite thành công (users, conversations, messages)!");
        } catch (SQLException e) {
            System.err.println("[DATABASE] Lỗi khởi tạo CSDL: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
