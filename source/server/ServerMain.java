import account.AccountHandler;
import account.UserDAO;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import database.DatabaseManager;
import file_notification.handler.FileNotificationHandler;
import group_chat.handler.GroupChatHandler;
import private_chat.handler.PrivateChatHandler;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ServerMain {
    public static final int PORT = 8888;
    private static final ExecutorService threadPool = Executors.newCachedThreadPool();

    // Map quản lý danh sách các Client đang Online (Key: username, Value: Luồng ghi PrintWriter)
    public static final Map<String, PrintWriter> onlineClients = new ConcurrentHashMap<>();

    public static void main(String[] args) {
        System.out.println("==================================================");
        System.out.println("   CHATNEST - SERVER                              ");
        System.out.println("==================================================");

        // Khởi tạo CSDL SQLite và reset trạng thái các user về OFFLINE
        DatabaseManager.initDatabase();

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
                    // Cắt bớt log nếu message quá dài (tránh spam console khi truyền base64)
                    String logMsg = message.length() > 200 ? message.substring(0, 200) + "... (độ dài: " + message.length() + ")" : message;
                    System.out.println("[NHẬN TỪ " + socket.getRemoteSocketAddress() + "]: " + logMsg);

                    try {
                        JsonObject req = JsonParser.parseString(message).getAsJsonObject();
                        String action = req.has("action") ? req.get("action").getAsString() : "";

                        // 1. Phân luồng các gói tin liên quan tới Tài khoản (Account)
                        if (AccountHandler.isAccountAction(action)) {
                            String loggedInUser = AccountHandler.handleAccountAction(message, writer, authenticatedUsername);

                            // Nếu xử lý trả về username tức là vừa ĐĂNG NHẬP THÀNH CÔNG
                            if (loggedInUser != null) {
                                this.authenticatedUsername = loggedInUser;
                                onlineClients.put(loggedInUser, writer);

                                // Lấy thông tin user để broadcast kèm họ tên cho các client khác
                                Map<String, String> userInfo = UserDAO.getUserByUsername(loggedInUser);
                                String fullName = userInfo != null ? userInfo.get("fullName") : loggedInUser;
                                String avatar = userInfo != null ? userInfo.get("avatar") : "";

                                // Broadcast thông báo cho các client khác biết user này vừa ONLINE
                                String statusJson = AccountHandler.createStatusChangeJson(loggedInUser, "ONLINE", fullName, avatar);
                                broadcast(statusJson, loggedInUser);
                            }
                        }
                        // 2. Phân luồng các gói tin liên quan tới Chat riêng 1-1 (Private Chat)
                        else if (PrivateChatHandler.isPrivateChatAction(action)) {
                            PrivateChatHandler.handlePrivateChatAction(req, writer, authenticatedUsername, onlineClients);
                        }
                        // 3. Phân luồng các gói tin liên quan tới Module 4: Truyền tải file & Thông báo (File & Notification)
                        else if (FileNotificationHandler.isFileNotificationAction(action)) {
                            FileNotificationHandler.handleFileNotificationAction(req, writer, authenticatedUsername, onlineClients);
                        }
                        
                        else {
                            JsonObject err = new JsonObject();
                            err.addProperty("action", "ERROR");
                            err.addProperty("status", "FAILED");
                            err.addProperty("message", "Hành động '" + action + "' không được hỗ trợ!");
                            writer.println(err.toString());
                        }
                    } catch (Exception ex) {
                        System.err.println("[SERVER] Lỗi xử lý gói tin JSON từ " + socket.getRemoteSocketAddress() + ": " + ex.getMessage());
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
