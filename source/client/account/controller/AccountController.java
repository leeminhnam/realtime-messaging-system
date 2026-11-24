package account.controller;

import account.model.LoginSession;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.swing.SwingUtilities;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.function.Consumer;
import java.util.function.BiConsumer;

/**
 * Controller của Module Account.
 * View lấy dữ liệu từ form và hiển thị phản hồi; Controller thực hiện giao tiếp TCP/JSON.
 * Các callback luôn chạy trên Swing Event Dispatch Thread (EDT).
 */
public final class AccountController {
    private AccountController() { }

    /** Đăng nhập bằng một Socket mới; nếu thành công, chuyển Socket cho MainFrame sử dụng tiếp. */
    public static void loginAsync(String host, int port, String username, String password,
            Consumer<LoginSession> onSuccess, Consumer<String> onFailure, Consumer<Exception> onError) {
        new Thread(() -> {
            Socket socket = null;
            try {
                socket = new Socket(host, port);
                PrintWriter writer = new PrintWriter(socket.getOutputStream(), true);
                BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));

                JsonObject req = new JsonObject();
                req.addProperty("action", "LOGIN");
                JsonObject data = new JsonObject();
                data.addProperty("username", username);
                data.addProperty("password", password);
                req.add("data", data);
                writer.println(req.toString());

                String resStr = reader.readLine();
                if (resStr == null) {
                    throw new java.io.IOException("Server đã đóng kết nối trong lúc đăng nhập");
                }
                JsonObject res = JsonParser.parseString(resStr).getAsJsonObject();
                String status = res.get("status").getAsString();
                String message = res.get("message").getAsString();

                if ("SUCCESS".equalsIgnoreCase(status)) {
                    // Phần socket này vẫn mở để Client tiếp tục chat sau đăng nhập.
                    LoginSession session = new LoginSession(socket, reader, writer, res.getAsJsonObject("data"));
                    SwingUtilities.invokeLater(() -> onSuccess.accept(session));
                } else {
                    socket.close();
                    SwingUtilities.invokeLater(() -> onFailure.accept(message));
                }
            } catch (Exception ex) {
                if (socket != null) {
                    try { socket.close(); } catch (Exception ignored) { }
                }
                SwingUtilities.invokeLater(() -> onError.accept(ex));
            }
        }, "chatnest-account-login").start();
    }

    /** Đăng ký dùng kết nối ngắn hạn, đóng Socket sau khi nhận phản hồi. */
    public static void registerAsync(String host, int port, String username, String password, String fullName,
            BiConsumer<String, String> onResponse, Consumer<Exception> onError) {
        new Thread(() -> {
            try (Socket socket = new Socket(host, port);
                    PrintWriter writer = new PrintWriter(socket.getOutputStream(), true);
                    BufferedReader reader = new BufferedReader(
                            new InputStreamReader(socket.getInputStream(), "UTF-8"))) {

                // Đóng gói JSON đăng ký
                JsonObject req = new JsonObject();
                req.addProperty("action", "REGISTER");
                JsonObject data = new JsonObject();
                data.addProperty("username", username);
                data.addProperty("password", password);
                data.addProperty("fullName", fullName);
                req.add("data", data);

                // Gửi sang Server
                writer.println(req.toString());

                // Đọc phản hồi
                String responseStr = reader.readLine();
                if (responseStr == null) {
                    throw new java.io.IOException("Server đã đóng kết nối trong lúc đăng ký");
                }
                JsonObject res = JsonParser.parseString(responseStr).getAsJsonObject();
                String status = res.get("status").getAsString();
                String message = res.get("message").getAsString();
                SwingUtilities.invokeLater(() -> onResponse.accept(status, message));
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> onError.accept(ex));
            }
        }, "chatnest-account-register").start();
    }

    /** Sử dụng Socket của MainFrame; phản hồi UPDATE_PROFILE_RESPONSE vẫn do MainFrame nhận. */
    public static void sendUpdateProfile(PrintWriter writer, String fullName) {
        JsonObject req = new JsonObject();
        req.addProperty("action", "UPDATE_PROFILE");
        JsonObject data = new JsonObject();
        data.addProperty("fullName", fullName);
        data.addProperty("avatar", ""); // chưa hỗ trợ đổi avatar
        req.add("data", data);
        writer.println(req.toString());
    }
}
