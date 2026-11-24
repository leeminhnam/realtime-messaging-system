package testutil;

import account.UserDAO;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import database.DatabaseManager;
import util.ConsoleUtf8;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * Tiện ích dùng chung cho các bộ test (không cần thư viện test ngoài):
 * - Khởi tạo CSDL SQLite tạm, không đụng tới messaging_app.db thật.
 * - Ghi nhận kết quả đạt/lỗi.
 * - Bắt các gói JSON mà handler ghi ra PrintWriter.
 */
public class TestSupport {
    private static int passed = 0;
    private static int failed = 0;
    private static boolean dbReady = false;

    // Tạo CSDL tạm (chỉ làm một lần) và tự dọn khi kết thúc tiến trình.
    public static synchronized void initTempDb() throws Exception {
        ConsoleUtf8.enable();
        if (dbReady) {
            return;
        }
        File tmp = Files.createTempDirectory("chatnest-test-").toFile();
        System.setProperty("chatnest.db.dir", tmp.getAbsolutePath());
        Runtime.getRuntime().addShutdownHook(new Thread(() -> deleteRecursively(tmp)));
        DatabaseManager.initDatabase();
        dbReady = true;
    }

    // Đăng ký nhanh các tài khoản test (họ tên = "Name of <username>").
    public static void registerUsers(String... usernames) {
        for (String u : usernames) {
            UserDAO.register(u, "pw", "Name of " + u);
        }
    }

    public static void section(String title) {
        System.out.println("\n== " + title + " ==");
    }

    public static void check(String name, boolean condition) {
        if (condition) {
            passed++;
            System.out.println("  [PASS] " + name);
        } else {
            failed++;
            System.out.println("  [FAIL] " + name);
        }
    }

    // In tổng kết và trả về mã thoát (0 = tất cả đạt).
    public static int summary() {
        System.out.println("\nKết quả: " + passed + " đạt, " + failed + " lỗi");
        return failed == 0 ? 0 : 1;
    }

    // Bộ đệm bắt các dòng JSON mà handler ghi ra (thay cho socket thật).
    public static class Capture {
        private final StringWriter sw = new StringWriter();
        public final PrintWriter writer = new PrintWriter(sw, true);

        public List<JsonObject> all() {
            List<JsonObject> list = new ArrayList<>();
            for (String line : sw.toString().split("\\R")) {
                if (!line.trim().isEmpty()) {
                    list.add(JsonParser.parseString(line.trim()).getAsJsonObject());
                }
            }
            return list;
        }

        // Gói tin cuối cùng đã ghi (null nếu chưa có).
        public JsonObject last() {
            List<JsonObject> list = all();
            return list.isEmpty() ? null : list.get(list.size() - 1);
        }

        // Gói tin đầu tiên có action chỉ định (null nếu không có).
        public JsonObject find(String action) {
            for (JsonObject o : all()) {
                if (o.has("action") && action.equals(o.get("action").getAsString())) {
                    return o;
                }
            }
            return null;
        }

        public boolean isEmpty() {
            return all().isEmpty();
        }

        public void clear() {
            sw.getBuffer().setLength(0);
        }
    }

    // Dựng gói request {"action":..., "data":{...}} từ chuỗi JSON data.
    public static JsonObject request(String action, String dataJson) {
        JsonObject req = new JsonObject();
        req.addProperty("action", action);
        if (dataJson != null) {
            req.add("data", JsonParser.parseString(dataJson));
        }
        return req;
    }

    public static boolean isStatus(JsonObject res, String action, String status) {
        return res != null && action.equals(res.get("action").getAsString())
                && status.equals(res.get("status").getAsString());
    }

    private static void deleteRecursively(File f) {
        File[] children = f.listFiles();
        if (children != null) {
            for (File c : children) {
                deleteRecursively(c);
            }
        }
        f.delete();
    }
}
