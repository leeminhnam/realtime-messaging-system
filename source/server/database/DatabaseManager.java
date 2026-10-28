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
     * Khởi tạo cấu trúc bảng CSDL SQLite khi Server khởi động.
     * Thiết lập chế độ WAL và tự động reset trạng thái các user về OFFLINE.
     */
    public static void initDatabase() {
        String createUsersTable = "CREATE TABLE IF NOT EXISTS users ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "username TEXT UNIQUE NOT NULL, "
                + "password TEXT NOT NULL, "
                + "full_name TEXT NOT NULL, "
                + "avatar TEXT DEFAULT '', "
                + "status TEXT DEFAULT 'OFFLINE', "
                + "created_at DATETIME DEFAULT CURRENT_TIMESTAMP"
                + ");";

        try (Connection conn = getConnection();
                Statement stmt = conn.createStatement()) {

            // Bật chế độ Write-Ahead Logging (WAL) để tăng tốc độ đọc/ghi đồng thời
            stmt.execute("PRAGMA journal_mode=WAL;");

            // Tạo bảng người dùng
            stmt.execute(createUsersTable);

            // Reset tất cả tài khoản về OFFLINE khi server khởi động lại
            // (Tránh trường hợp bị kẹt trạng thái ONLINE khi tắt server đột ngột)
            stmt.executeUpdate("UPDATE users SET status = 'OFFLINE';");

            System.out.println("[DATABASE] Kết nối và khởi tạo CSDL SQLite thành công!");
        } catch (SQLException e) {
            System.err.println("[DATABASE] Lỗi khởi tạo CSDL: " + e.getMessage());
        }
    }
}
