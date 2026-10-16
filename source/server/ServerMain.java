import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// Điểm khởi chạy của Server - Hệ thống nhắn tin thời gian thực
public class ServerMain {
    public static final int PORT = 8888;
    // Sử dụng ThreadPool để quản lý đồng thời nhiều client kết nối
    private static final ExecutorService threadPool = Executors.newCachedThreadPool();

    public static void main(String[] args) {
        System.out.println("   HỆ THỐNG NHẮN TIN THỜI GIAN THỰC - SERVER      ");

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("[SERVER] Đang lắng nghe tại cổng: " + PORT);
            System.out.println("[SERVER] Sẵn sàng tiếp nhận kết nối từ Client...\n");

            while (true) {
                // Chờ đợi client kết nối
                Socket clientSocket = serverSocket.accept();
                System.out.println("[SERVER] Client mới kết nối từ: "
                        + clientSocket.getRemoteSocketAddress());

                // Giao việc xử lý client cho một luồng riêng
                threadPool.execute(new ClientHandler(clientSocket));
            }
        } catch (IOException e) {
            System.err.println("[SERVER] Lỗi khởi động hoặc lắng nghe: " + e.getMessage());
            e.printStackTrace();
        } finally {
            threadPool.shutdown();
        }
    }

    // Luồng xử lý giao tiếp riêng cho từng Client kết nối tới
    static class ClientHandler implements Runnable {
        private final Socket socket;

        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try (
                BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                PrintWriter writer = new PrintWriter(socket.getOutputStream(), true)
            ) {
                // Gửi thông điệp chào mừng khi client kết nối thành công
                writer.println("WELCOME Chào mừng bạn đến với Hệ thống nhắn tin!");

                String message;
                while ((message = reader.readLine()) != null) {
                    System.out.println("[NHẬN TỪ " + socket.getRemoteSocketAddress() + "]: " + message);

                    // TODO: Định tuyến thông điệp tới các module:
                    // 1. account: Đăng ký, đăng nhập, danh sách online/offline
                    // 2. private-chat: Gửi/nhận tin nhắn 1-1
                    // 3. group-chat: Gửi tin nhắn nhóm, tạo nhóm
                    // 4. file-notification: Upload/download file, thông báo

                    // Ví dụ echo phản hồi lại client
                    writer.println("ECHO " + message);
                }
            } catch (IOException e) {
                System.out.println("[SERVER] Client " + socket.getRemoteSocketAddress() + " đã ngắt kết nối.");
            } finally {
                try {
                    socket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
