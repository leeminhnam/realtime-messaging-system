package account;

import account.dao.UserDAO;
import account.handler.AccountHandler;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import database.DatabaseManager;

import java.io.PrintWriter;
import java.io.StringWriter;
import testutil.TestSupport;

import java.util.Map;

/**
 * Kiểm thử Module 1 (Account) - không cần thư viện test ngoài.
 * Dùng CSDL SQLite tạm (thư mục tạm), KHÔNG ảnh hưởng tới messaging_app.db thật.
 * Chạy: xem mục "Kiểm thử nhanh" trong README.md. Thoát với mã 1 nếu có test lỗi.
 */
public class AccountModuleTest {
    public static void main(String[] args) throws Exception {
        TestSupport.initTempDb();
        run();
        System.exit(TestSupport.summary());
    }

    public static void run() throws Exception {
        TestSupport.section("MODULE 1 - ACCOUNT");
        testRegisterSuccess();
        testRegisterDuplicateIgnoreCase();
        testPasswordIsHashed();
        testLoginSuccessSetsOnline();
        testLoginWrongPassword();
        testLoginUnknownUser();
        testUpdateStatusOffline();
        testUpdateProfile();
        testGetAllUsers();
        testHandlerRegisterEmptyField();
        testHandlerRegisterAndLoginJson();
        testHandlerLoginFailedJson();
        testHandlerUpdateProfileNotLoggedIn();
        testHandlerInvalidJson();
        testIsAccountAction();
        testStatusChangeJson();
    }

    // UserDAO 
    static void testRegisterSuccess() {
        check("register: tài khoản mới thành công", UserDAO.register("alice", "pw123", "Alice Nguyen"));
    }

    static void testRegisterDuplicateIgnoreCase() {
        check("register: trùng username (không phân biệt hoa/thường) bị từ chối",
                !UserDAO.register("ALICE", "other", "Fake Alice"));
    }

    static void testPasswordIsHashed() throws Exception {
        try (var c = DatabaseManager.getConnection();
                var rs = c.createStatement().executeQuery("SELECT password FROM users WHERE username='alice'")) {
            rs.next();
            String stored = rs.getString(1);
            check("register: mật khẩu lưu dạng BCrypt, không phải plaintext",
                    !stored.equals("pw123") && stored.startsWith("$2"));
        }
    }

    static void testLoginSuccessSetsOnline() {
        Map<String, String> u = UserDAO.login("alice", "pw123");
        check("login: đúng mật khẩu trả về thông tin user", u != null && "Alice Nguyen".equals(u.get("fullName")));
        check("login: trạng thái trong DB chuyển ONLINE",
                "ONLINE".equals(UserDAO.getUserByUsername("alice").get("status")));
    }

    static void testLoginWrongPassword() {
        check("login: sai mật khẩu trả về null", UserDAO.login("alice", "wrong") == null);
    }

    static void testLoginUnknownUser() {
        check("login: user không tồn tại trả về null", UserDAO.login("ghost", "x") == null);
    }

    static void testUpdateStatusOffline() {
        UserDAO.updateStatus("alice", "OFFLINE");
        check("updateStatus: chuyển OFFLINE", "OFFLINE".equals(UserDAO.getUserByUsername("alice").get("status")));
    }

    static void testUpdateProfile() {
        check("updateProfile: user tồn tại -> true", UserDAO.updateProfile("alice", "Alice Tran", "avatar.png"));
        Map<String, String> u = UserDAO.getUserByUsername("alice");
        check("updateProfile: dữ liệu mới được lưu",
                "Alice Tran".equals(u.get("fullName")) && "avatar.png".equals(u.get("avatar")));
        check("updateProfile: user không tồn tại -> false", !UserDAO.updateProfile("ghost", "X", ""));
    }

    static void testGetAllUsers() {
        UserDAO.register("bob", "pw", "Bob");
        var names = UserDAO.getAllUsers().stream().map(u -> u.get("username")).toList();
        check("getAllUsers: trả về cả alice và bob", names.contains("alice") && names.contains("bob"));
        check("getUserByUsername: không tồn tại -> null", UserDAO.getUserByUsername("ghost") == null);
    }

    // AccountHandler (giao thức JSON) 
    static void testHandlerRegisterEmptyField() {
        JsonObject res = send("{\"action\":\"REGISTER\",\"data\":{\"username\":\"\",\"password\":\"p\",\"fullName\":\"N\"}}", null);
        check("handler REGISTER: trường rỗng -> FAILED",
                "REGISTER_RESPONSE".equals(res.get("action").getAsString())
                        && "FAILED".equals(res.get("status").getAsString()));
    }

    static void testHandlerRegisterAndLoginJson() {
        JsonObject reg = send("{\"action\":\"REGISTER\",\"data\":{\"username\":\"carol\",\"password\":\"pw\",\"fullName\":\"Carol\"}}", null);
        check("handler REGISTER: hợp lệ -> SUCCESS", "SUCCESS".equals(reg.get("status").getAsString()));

        StringWriter sw = new StringWriter();
        String user = AccountHandler.handleAccountAction(
                "{\"action\":\"LOGIN\",\"data\":{\"username\":\"carol\",\"password\":\"pw\"}}", new PrintWriter(sw, true), null);
        JsonObject res = JsonParser.parseString(sw.toString().trim()).getAsJsonObject();
        check("handler LOGIN: trả về username để server lưu phiên", "carol".equals(user));
        check("handler LOGIN: SUCCESS và có danh sách allUsers",
                "SUCCESS".equals(res.get("status").getAsString())
                        && res.getAsJsonObject("data").getAsJsonArray("allUsers").size() >= 3);
    }

    static void testHandlerLoginFailedJson() {
        StringWriter sw = new StringWriter();
        String user = AccountHandler.handleAccountAction(
                "{\"action\":\"LOGIN\",\"data\":{\"username\":\"carol\",\"password\":\"bad\"}}", new PrintWriter(sw, true), null);
        JsonObject res = JsonParser.parseString(sw.toString().trim()).getAsJsonObject();
        check("handler LOGIN: sai mật khẩu -> FAILED và không tạo phiên",
                user == null && "FAILED".equals(res.get("status").getAsString()));
    }

    static void testHandlerUpdateProfileNotLoggedIn() {
        JsonObject res = send("{\"action\":\"UPDATE_PROFILE\",\"data\":{\"fullName\":\"X\"}}", null);
        check("handler UPDATE_PROFILE: chưa đăng nhập -> FAILED", "FAILED".equals(res.get("status").getAsString()));

        JsonObject ok = send("{\"action\":\"UPDATE_PROFILE\",\"data\":{\"fullName\":\"Bob Le\"}}", "bob");
        check("handler UPDATE_PROFILE: đã đăng nhập -> SUCCESS", "SUCCESS".equals(ok.get("status").getAsString()));

        JsonObject list = send("{\"action\":\"GET_USER_LIST\"}", "bob");
        JsonArray users = list.getAsJsonObject("data").getAsJsonArray("users");
        check("handler GET_USER_LIST: trả về danh sách người dùng", users.size() >= 3);
    }

    static void testHandlerInvalidJson() {
        JsonObject res = send("{không phải json", null);
        check("handler: JSON sai định dạng -> ERROR/FAILED", "FAILED".equals(res.get("status").getAsString()));
    }

    static void testIsAccountAction() {
        check("isAccountAction: nhận đúng 4 action, không phân biệt hoa/thường",
                AccountHandler.isAccountAction("login") && AccountHandler.isAccountAction("REGISTER")
                        && AccountHandler.isAccountAction("UPDATE_PROFILE") && AccountHandler.isAccountAction("GET_USER_LIST"));
        check("isAccountAction: từ chối action của module khác", !AccountHandler.isAccountAction("SEND_PRIVATE_MESSAGE"));
    }

    static void testStatusChangeJson() {
        JsonObject full = JsonParser.parseString(
                AccountHandler.createStatusChangeJson("alice", "ONLINE", "Alice", "a.png")).getAsJsonObject();
        JsonObject d = full.getAsJsonObject("data");
        check("USER_STATUS_CHANGE: đủ action/username/status/fullName/avatar",
                "USER_STATUS_CHANGE".equals(full.get("action").getAsString())
                        && "alice".equals(d.get("username").getAsString())
                        && "ONLINE".equals(d.get("status").getAsString())
                        && d.has("fullName") && d.has("avatar"));
        JsonObject min = JsonParser.parseString(AccountHandler.createStatusChangeJson("alice", "OFFLINE"))
                .getAsJsonObject().getAsJsonObject("data");
        check("USER_STATUS_CHANGE: bản OFFLINE không kèm fullName/avatar", !min.has("fullName") && !min.has("avatar"));
    }

    // Tiện ích 
    static JsonObject send(String json, String currentUser) {
        StringWriter sw = new StringWriter();
        AccountHandler.handleAccountAction(json, new PrintWriter(sw, true), currentUser);
        return JsonParser.parseString(sw.toString().trim()).getAsJsonObject();
    }

    static void check(String name, boolean cond) {
        TestSupport.check(name, cond);
    }
}
