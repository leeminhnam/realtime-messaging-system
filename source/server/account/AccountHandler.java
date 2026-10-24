package account;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.PrintWriter;
import java.util.List;
import java.util.Map;

public class AccountHandler {
    private static final Gson gson = new Gson();

    /**
     * Xử lý chính cho tất cả các hành động liên quan tới Module Account
     * 
     * @param jsonReq         Chuỗi JSON yêu cầu từ Client
     * @param writer          Luồng ghi dữ liệu riêng của Client hiện tại
     * @param currentUsername Tên đăng nhập của kết nối hiện tại (truyền theo dạng
     *                        mảng 1 phần tử để cập nhật sau khi LOGIN thành công)
     * @return Chuỗi username nếu vừa ĐĂNG NHẬP thành công, ngược lại trả về null
     */
    public static String handleAccountAction(String jsonReq, PrintWriter writer, String currentUsername) {
        try {
            JsonObject req = JsonParser.parseString(jsonReq).getAsJsonObject();
            String action = req.has("action") ? req.get("action").getAsString() : "";
            JsonObject data = req.has("data") && !req.get("data").isJsonNull() ? req.getAsJsonObject("data")
                    : new JsonObject();

            switch (action) {
                case "REGISTER":
                    handleRegister(data, writer);
                    break;

                case "LOGIN":
                    return handleLogin(data, writer);

                case "UPDATE_PROFILE":
                    handleUpdateProfile(currentUsername, data, writer);
                    break;

                case "GET_USER_LIST":
                    handleGetUserList(writer);
                    break;

                default:
                    sendResponse(writer, "ERROR", "FAILED", "Hành động không hợp lệ!", null);
                    break;
            }
        } catch (Exception e) {
            System.err.println("[AccountHandler] Lỗi xử lý JSON: " + e.getMessage());
            sendResponse(writer, "ERROR", "FAILED", "Định dạng gói tin JSON không hợp lệ!", null);
        }
        return null;
    }

    // 1. Xử lý Đăng ký
    private static void handleRegister(JsonObject data, PrintWriter writer) {
        String username = data.get("username").getAsString();
        String password = data.get("password").getAsString();
        String fullName = data.get("fullName").getAsString();

        if (username.isEmpty() || password.isEmpty() || fullName.isEmpty()) {
            sendResponse(writer, "REGISTER_RESPONSE", "FAILED", "Thông tin nhập vào không được để trống!", null);
            return;
        }

        boolean ok = UserDAO.register(username, password, fullName);
        if (ok) {
            sendResponse(writer, "REGISTER_RESPONSE", "SUCCESS", "Đăng ký tài khoản thành công!", null);
        } else {
            sendResponse(writer, "REGISTER_RESPONSE", "FAILED", "Tên đăng nhập đã tồn tại hoặc có lỗi CSDL!", null);
        }
    }

    // 2. Xử lý Đăng nhập
    private static String handleLogin(JsonObject data, PrintWriter writer) {
        String username = data.get("username").getAsString();
        String password = data.get("password").getAsString();

        Map<String, String> user = UserDAO.login(username, password);

        if (user != null) {
            JsonObject resData = new JsonObject();
            resData.addProperty("username", user.get("username"));
            resData.addProperty("fullName", user.get("fullName"));
            resData.addProperty("avatar", user.get("avatar"));
            resData.addProperty("status", "ONLINE");

            // Lấy danh sách toàn bộ người dùng gửi về cho client vừa đăng nhập
            List<Map<String, String>> allUsers = UserDAO.getAllUsers();
            resData.add("allUsers", gson.toJsonTree(allUsers));

            sendResponse(writer, "LOGIN_RESPONSE", "SUCCESS", "Đăng nhập thành công!", resData);
            return username; // Trả về username để ClientHandler lưu lại phiên
        } else {
            sendResponse(writer, "LOGIN_RESPONSE", "FAILED", "Sai tên đăng nhập hoặc mật khẩu!", null);
            return null;
        }
    }

    // 3. Xử lý Cập nhật hồ sơ
    private static void handleUpdateProfile(String currentUsername, JsonObject data, PrintWriter writer) {
        if (currentUsername == null || currentUsername.isEmpty()) {
            sendResponse(writer, "UPDATE_PROFILE_RESPONSE", "FAILED", "Bạn chưa đăng nhập!", null);
            return;
        }

        String fullName = data.get("fullName").getAsString();
        String avatar = data.has("avatar") ? data.get("avatar").getAsString() : "";

        boolean ok = UserDAO.updateProfile(currentUsername, fullName, avatar);
        if (ok) {
            JsonObject resData = new JsonObject();
            resData.addProperty("username", currentUsername);
            resData.addProperty("fullName", fullName);
            resData.addProperty("avatar", avatar);
            sendResponse(writer, "UPDATE_PROFILE_RESPONSE", "SUCCESS", "Cập nhật hồ sơ thành công!", resData);
        } else {
            sendResponse(writer, "UPDATE_PROFILE_RESPONSE", "FAILED", "Lỗi cập nhật CSDL!", null);
        }
    }

    // 4. Lấy danh sách người dùng
    private static void handleGetUserList(PrintWriter writer) {
        List<Map<String, String>> allUsers = UserDAO.getAllUsers();
        JsonObject resData = new JsonObject();
        resData.add("users", gson.toJsonTree(allUsers));
        sendResponse(writer, "GET_USER_LIST_RESPONSE", "SUCCESS", "Tải danh sách thành công", resData);
    }

    // Hàm tiện ích đóng gói JSON gửi về Client
    public static void sendResponse(PrintWriter writer, String action, String status, String message, JsonObject data) {
        JsonObject res = new JsonObject();
        res.addProperty("action", action);
        res.addProperty("status", status);
        res.addProperty("message", message);
        if (data != null) {
            res.add("data", data);
        }
        writer.println(gson.toJson(res));
    }

    // Tạo chuỗi JSON phát thông báo đổi trạng thái Online/Offline
    public static String createStatusChangeJson(String username, String status) {
        JsonObject res = new JsonObject();
        res.addProperty("action", "USER_STATUS_CHANGE");
        JsonObject data = new JsonObject();
        data.addProperty("username", username);
        data.addProperty("status", status);
        res.add("data", data);
        return gson.toJson(res);
    }
}