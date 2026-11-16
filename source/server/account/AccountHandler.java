package account;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.PrintWriter;
import java.util.List;
import java.util.Map;

public class AccountHandler {
    private static final Gson gson = new Gson();

    // Các action do AccountHandler xử lý
    public static boolean isAccountAction(String action) {
        return "REGISTER".equalsIgnoreCase(action)
                || "LOGIN".equalsIgnoreCase(action)
                || "UPDATE_PROFILE".equalsIgnoreCase(action)
                || "GET_USER_LIST".equalsIgnoreCase(action);
    }

    // Trả về username nếu vừa đăng nhập thành công, còn lại trả về null
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

    // Đăng ký
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

    // Đăng nhập
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

            // Gửi kèm danh sách người dùng để Client dựng danh bạ luôn
            List<Map<String, String>> allUsers = UserDAO.getAllUsers();
            resData.add("allUsers", gson.toJsonTree(allUsers));

            sendResponse(writer, "LOGIN_RESPONSE", "SUCCESS", "Đăng nhập thành công!", resData);
            return username; // ServerMain lưu username này cho kết nối
        } else {
            sendResponse(writer, "LOGIN_RESPONSE", "FAILED", "Sai tên đăng nhập hoặc mật khẩu!", null);
            return null;
        }
    }

    // Cập nhật hồ sơ
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

    // Lấy danh sách người dùng
    private static void handleGetUserList(PrintWriter writer) {
        List<Map<String, String>> allUsers = UserDAO.getAllUsers();
        JsonObject resData = new JsonObject();
        resData.add("users", gson.toJsonTree(allUsers));
        sendResponse(writer, "GET_USER_LIST_RESPONSE", "SUCCESS", "Tải danh sách thành công", resData);
    }

    // Ghi một gói phản hồi
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

    // Gói USER_STATUS_CHANGE để broadcast
    public static String createStatusChangeJson(String username, String status, String fullName, String avatar) {
        JsonObject res = new JsonObject();
        res.addProperty("action", "USER_STATUS_CHANGE");
        JsonObject data = new JsonObject();
        data.addProperty("username", username);
        data.addProperty("status", status);
        if (fullName != null) {
            data.addProperty("fullName", fullName);
        }
        if (avatar != null) {
            data.addProperty("avatar", avatar);
        }
        res.add("data", data);
        return gson.toJson(res);
    }

    public static String createStatusChangeJson(String username, String status) {
        return createStatusChangeJson(username, status, null, null);
    }
}