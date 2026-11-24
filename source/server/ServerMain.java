import account.AccountHandler;
import account.UserDAO;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import database.DatabaseManager;
import file_notification.handler.FileNotificationHandler;
import group_chat.handler.GroupChatHandler;
import private_chat.handler.PrivateChatHandler;
import util.ConsoleUtf8;

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

    // username -> luồng ghi của các Client đang online
    public static final Map<String, PrintWriter> onlineClients = new ConcurrentHashMap<>();

    public static void main(String[] args) {
        ConsoleUtf8.enable();

        System.out.println("CHATNEST - SERVER");

        // Tạo bảng nếu chưa có, đưa mọi tài khoản về OFFLINE
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

    // Gửi một gói tới mọi Client đang online
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
                    // Gói SEND_FILE rất dài nên chỉ in một đoạn đầu
                    String logMsg = message.length() > 200 ? message.substring(0, 200) + "... (độ dài: " + message.length() + ")" : message;
                    System.out.println("[NHẬN TỪ " + socket.getRemoteSocketAddress() + "]: " + logMsg);

                    try {
                        JsonObject req = JsonParser.parseString(message).getAsJsonObject();
                        String action = req.has("action") ? req.get("action").getAsString() : "";

                        // Tài khoản
                        if (AccountHandler.isAccountAction(action)) {
                            String loggedInUser = AccountHandler.handleAccountAction(message, writer, authenticatedUsername);

                            // Trả về username nghĩa là vừa đăng nhập thành công
                            if (loggedInUser != null) {
                                this.authenticatedUsername = loggedInUser;
                                onlineClients.put(loggedInUser, writer);

                                // Lấy họ tên để gửi kèm khi broadcast
                                Map<String, String> userInfo = UserDAO.getUserByUsername(loggedInUser);
                                String fullName = userInfo != null ? userInfo.get("fullName") : loggedInUser;
                                String avatar = userInfo != null ? userInfo.get("avatar") : "";

                                // Báo cho các Client khác là người này vừa online
                                String statusJson = AccountHandler.createStatusChangeJson(loggedInUser, "ONLINE", fullName, avatar);
                                broadcast(statusJson, loggedInUser);
                            }
                        }
                        // Chat riêng
                        else if (PrivateChatHandler.isPrivateChatAction(action)) {
                            PrivateChatHandler.handlePrivateChatAction(req, writer, authenticatedUsername, onlineClients);
                        }
                        // Gửi, tải tệp
                        else if (FileNotificationHandler.isFileNotificationAction(action)) {
                            FileNotificationHandler.handleFileNotificationAction(req, writer, authenticatedUsername, onlineClients);
                        }
                        // Chat nhóm
                        else if (GroupChatHandler.isGroupChatAction(action)) {
                            GroupChatHandler.handleGroupChatAction(req, writer, authenticatedUsername, onlineClients);
                        }
                        // Action không thuộc nhóm nào
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
                // Client thoát hoặc mất kết nối thì dọn dẹp ở đây
                if (authenticatedUsername != null) {
                    onlineClients.remove(authenticatedUsername);
                    UserDAO.updateStatus(authenticatedUsername, "OFFLINE");

                    // Báo cho mọi người là đã offline
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
