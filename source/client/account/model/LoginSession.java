package account.model;

import com.google.gson.JsonObject;
import java.io.BufferedReader;
import java.io.PrintWriter;
import java.net.Socket;

/**
 * Kết quả đăng nhập thành công: giữ Socket để MainFrame tiếp tục trao đổi dữ liệu.
 * Không đóng kết nối tại Controller vì Client dùng lại kết nối TCP này sau khi đăng nhập.
 */
public class LoginSession {
    private final Socket socket;
    private final BufferedReader reader;
    private final PrintWriter writer;
    private final JsonObject userData;

    public LoginSession(Socket socket, BufferedReader reader, PrintWriter writer, JsonObject userData) {
        this.socket = socket;
        this.reader = reader;
        this.writer = writer;
        this.userData = userData;
    }

    public Socket getSocket() { return socket; }
    public BufferedReader getReader() { return reader; }
    public PrintWriter getWriter() { return writer; }
    public JsonObject getUserData() { return userData; }
}
