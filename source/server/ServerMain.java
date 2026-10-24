import account.AccountHandler;
import account.UserDAO;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ServerMain {
    public static final int PORT = 8888;
    private static final String DB_URL = "jdbc:sqlite:source/server/data/messaging_app.db";
    private static final ExecutorService threadPool = Executors.newCachedThreadPool();

    // Map quản lý danh sách các Client đang Online (Key: username, Value: Luồng ghi
    // PrintWriter)
    public static final Map<String, PrintWriter> onlineClients = new ConcurrentHashMap<>();

    public static void main(String[] args) {
        System.out.println("   HỆ THỐNG NHẮN TIN THỜI GIAN THỰC - SERVER      ");

        initDatabase();

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("[SERVER] Đang lắng nghe tại cổng: " + PORT);
            System.out.println("[SERVER] Sẵn sàng tiếp nhận kết nối từ Client...\n");

            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("[SERVER] Client mới kết nối từ: " + clientSocket.getRemoteSocketAddress());

                threadPool.execute(new ClientHandler(clientSocket));
            }
        } catch (IOException e) {
            System.err.println("[SERVER] Lỗi khởi động hoặc lắng nghe: " + e.getMessage());
            e.printStackTrace();
        } finally {
            threadPool.shutdown();
        }
    }

    private static void initDatabase() {
        String createTableSQL = "CREATE TABLE IF NOT EXISTS users ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "username TEXT UNIQUE NOT NULL, "
                + "password TEXT NOT NULL, "
                + "full_name TEXT NOT NULL, "
                + "avatar TEXT DEFAULT '', "
                + "status TEXT DEFAULT 'OFFLINE', "
                + "created_at DATETIME DEFAULT CURRENT_TIMESTAMP"
                + ");";

        try (Connection conn = DriverManager.getConnection(DB_URL);
                Statement stmt = conn.createStatement()) {
            stmt.execute("PRAGMA journal_mode=WAL;");
            stmt.execute(createTableSQL);
            System.out.println("[DATABASE] Kết nối và khởi tạo CSDL SQLite thành công!");
        } catch (Exception e) {
            System.err.println("[DATABASE] Lỗi khởi tạo CSDL: " + e.getMessage());
        }
    }

    // Phát thông báo JSON tới tất cả Client đang Online
    public static void broadcast(String jsonMsg, String excludeUsername) {
        for (Map.Entry<String, PrintWriter> entry : onlineClients.entrySet()) {
            if (excludeUsername == null || !entry.getKey().equalsIgnoreCase(excludeUsername)) {
                entry.getValue().println(jsonMsg);
            }
        }
    }

    static class ClientHandler implements Runnable {
        private final Socket socket;
        private String authenticatedUsername = null;

        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try (
                    BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
                    PrintWriter writer = new PrintWriter(socket.getOutputStream(), true)) {
                String message;
                while ((message = reader.readLine()) != null) {
                    System.out.println("[NHẬN TỪ " + socket.getRemoteSocketAddress() + "]: " + message);

                    // Cho AccountHandler xử lý gói tin
                    String loggedInUser = AccountHandler.handleAccountAction(message, writer, authenticatedUsername);

                    // Nếu xử lý trả về username tức là vừa ĐĂNG NHẬP THÀNH CÔNG
                    if (loggedInUser != null) {
                        this.authenticatedUsername = loggedInUser;
                        onlineClients.put(loggedInUser, writer);

                        // Broadcast thông báo cho các client khác biết user này vừa ONLINE
                        String statusJson = AccountHandler.createStatusChangeJson(loggedInUser, "ONLINE");
                        broadcast(statusJson, loggedInUser);
                    }
                }
            } catch (IOException e) {
                System.out.println("[SERVER] Client " + socket.getRemoteSocketAddress() + " đã ngắt kết nối.");
            } finally {
                // Xử lý khi Client thoát hoặc mất kết nối đột ngột
                if (authenticatedUsername != null) {
                    onlineClients.remove(authenticatedUsername);
                    UserDAO.updateStatus(authenticatedUsername, "OFFLINE");

                    // Broadcast thông báo user vừa OFFLINE
                    String statusJson = AccountHandler.createStatusChangeJson(authenticatedUsername, "OFFLINE");
                    broadcast(statusJson, authenticatedUsername);
                    System.out.println("[SERVER] User '" + authenticatedUsername + "' chuyển sang OFFLINE.");
                }
                try {
                    socket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}