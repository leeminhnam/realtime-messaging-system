/**
 * Kiem thu mang (Server that + nhieu Client socket). Chay sau khi bien dich: java -cp "out_test;source/lib/*" NetworkTest
 * Dung CSDL tam (-Dchatnest.db.dir) va cong 8888: hay tat Server dang chay truoc khi chay.
 */
import com.google.gson.*;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** Bo do kiem thu mang cho ChatNest (chay server that + nhieu client socket that). */
public class NetworkTest {
    static final Gson gson = new Gson();

    static class Cli implements AutoCloseable {
        Socket s; BufferedReader r; PrintWriter w;
        final List<JsonObject> inbox = Collections.synchronizedList(new ArrayList<>());
        final String user;
        Cli(String user) throws Exception {
            this.user = user;
            s = new Socket("localhost", 8888);
            r = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
            w = new PrintWriter(new OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8), true);
            Thread t = new Thread(() -> {
                try {
                    String line;
                    while ((line = r.readLine()) != null) {
                        try { inbox.add(JsonParser.parseString(line).getAsJsonObject()); } catch (Exception ignored) {}
                    }
                } catch (IOException ignored) {}
            });
            t.setDaemon(true); t.start();
        }
        void send(String action, JsonObject data) {
            JsonObject o = new JsonObject(); o.addProperty("action", action);
            if (data != null) o.add("data", data);
            w.println(gson.toJson(o));
        }
        JsonObject take(String action, long timeoutMs) throws Exception {
            long end = System.currentTimeMillis() + timeoutMs;
            while (System.currentTimeMillis() < end) {
                synchronized (inbox) {
                    for (Iterator<JsonObject> it = inbox.iterator(); it.hasNext();) {
                        JsonObject o = it.next();
                        if (o.has("action") && o.get("action").getAsString().equals(action)) { it.remove(); return o; }
                    }
                }
                Thread.sleep(2);
            }
            return null;
        }
        JsonObject takeWhere(String action, String field, String value, long timeoutMs) throws Exception {
            long end = System.currentTimeMillis() + timeoutMs;
            while (System.currentTimeMillis() < end) {
                synchronized (inbox) {
                    for (Iterator<JsonObject> it = inbox.iterator(); it.hasNext();) {
                        JsonObject o = it.next();
                        if (o.has("action") && o.get("action").getAsString().equals(action)
                                && o.has("data") && o.getAsJsonObject("data").has(field)
                                && o.getAsJsonObject("data").get(field).getAsString().equals(value)) { it.remove(); return o; }
                    }
                }
                Thread.sleep(2);
            }
            return null;
        }
        boolean register() throws Exception {
            JsonObject d = new JsonObject(); d.addProperty("username", user); d.addProperty("password", "pw"); d.addProperty("fullName", "User " + user);
            send("REGISTER", d); JsonObject res = take("REGISTER_RESPONSE", 30000);
            return res != null && res.get("status").getAsString().equals("SUCCESS");
        }
        boolean login() throws Exception {
            JsonObject d = new JsonObject(); d.addProperty("username", user); d.addProperty("password", "pw");
            send("LOGIN", d); JsonObject res = take("LOGIN_RESPONSE", 30000);
            return res != null && res.get("status").getAsString().equals("SUCCESS");
        }
        public void close() { try { s.close(); } catch (Exception ignored) {} }
    }

    static JsonObject obj(String... kv) { JsonObject o = new JsonObject(); for (int i = 0; i < kv.length; i += 2) o.addProperty(kv[i], kv[i + 1]); return o; }
    static String sha(byte[] b) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(b)); }
    static void row(String id, String name, String result, String detail) { System.out.println("RESULT|" + id + "|" + name + "|" + result + "|" + detail); }

    public static void main(String[] a) {
        try { run(); } catch (Throwable t) { t.printStackTrace(); }
        System.exit(0);
    }

    static void run() throws Exception {
        File tmp = Files.createTempDirectory("chatnest-net-").toFile();
        System.setProperty("chatnest.db.dir", tmp.getAbsolutePath());
        Thread srv = new Thread(() -> ServerMain.main(new String[0])); srv.setDaemon(true); srv.start();
        Thread.sleep(1500);

        // ---------- T1: ket noi / ngat ket noi ----------
        try (Cli A = new Cli("net_a"); Cli B = new Cli("net_b")) {
            boolean reg = A.register() & B.register();
            boolean la = A.login();
            long t0 = System.nanoTime();
            boolean lb = B.login();
            JsonObject on = A.takeWhere("USER_STATUS_CHANGE", "username", "net_b", 5000);
            long onMs = (System.nanoTime() - t0) / 1_000_000;
            boolean ok1 = reg && la && lb && on != null && on.getAsJsonObject("data").get("status").getAsString().equals("ONLINE");
            row("T1.1", "Dang ky, dang nhap 2 client; A nhan thong bao B ONLINE", ok1 ? "DAT" : "LOI", onMs + " ms (tu luc B gui LOGIN den luc A nhan thong bao)");
            long t1 = System.nanoTime();
            B.close();
            JsonObject off = A.takeWhere("USER_STATUS_CHANGE", "username", "net_b", 5000);
            long offMs = (System.nanoTime() - t1) / 1_000_000;
            boolean ok2 = off != null && off.getAsJsonObject("data").get("status").getAsString().equals("OFFLINE");
            row("T1.2", "B dong ket noi; A nhan thong bao B OFFLINE", ok2 ? "DAT" : "LOI", offMs + " ms");
        }

        // ---------- T2: nhieu client dong thoi ----------
        int[][] runs = { { 10, 0 }, { 50, 0 }, { 100, 0 }, { 100, 10 } };
        for (int[] run : runs) {
            int n = run[0], stagger = run[1];
            String pre = "m" + n + "s" + stagger + "_";
            List<Cli> cs = Collections.synchronizedList(new ArrayList<>());
            AtomicInteger refused = new AtomicInteger();
            long t0 = System.nanoTime();
            ExecutorService ex = Executors.newFixedThreadPool(n);
            List<Future<Boolean>> fs = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                final int idx = i;
                fs.add(ex.submit(() -> {
                    try { Cli c = new Cli(pre + idx); cs.add(c); return c.register() && c.login(); }
                    catch (java.net.ConnectException ce) { refused.incrementAndGet(); return false; }
                }));
                if (stagger > 0) Thread.sleep(stagger);
            }
            int okLogin = 0; for (Future<Boolean> f : fs) if (f.get()) okLogin++;
            long loginMs = (System.nanoTime() - t0) / 1_000_000;
            String mode = stagger == 0 ? "ket noi dong loat" : "vao lan luot cach nhau " + stagger + " ms";
            if (okLogin < n) {
                row("T2." + n + "." + stagger, n + " client " + mode, "LOI MOT PHAN", "dang nhap thanh cong " + okLogin + "/" + n + ", bi tu choi ket noi " + refused.get() + " trong " + loginMs + " ms");
            } else {
                Map<String, Cli> byUser = new HashMap<>(); for (Cli c : cs) byUser.put(c.user, c);
                long t1 = System.nanoTime();
                for (int i = 0; i < n; i++) byUser.get(pre + i).send("SEND_PRIVATE_MESSAGE", obj("receiver", pre + ((i + 1) % n), "content", "hello " + i));
                int delivered = 0;
                long deadline = System.currentTimeMillis() + 8000;
                for (int i = 0; i < n; i++) {
                    Cli rc = byUser.get(pre + ((i + 1) % n));
                    if (rc.takeWhere("RECEIVE_PRIVATE_MESSAGE", "content", "hello " + i, Math.max(50, deadline - System.currentTimeMillis())) != null) delivered++;
                }
                long msgMs = (System.nanoTime() - t1) / 1_000_000;
                row("T2." + n + "." + stagger, n + " client " + mode + ", moi client gui 1 tin cho client ke tiep", delivered == n ? "DAT" : "LOI MOT PHAN",
                        "dang nhap " + okLogin + "/" + n + " trong " + loginMs + " ms; tin den noi " + delivered + "/" + n + " trong " + msgMs + " ms");
            }
            for (Cli c : cs) c.close();
            ex.shutdownNow();
            Thread.sleep(800);
        }

        // ---------- T3: trao doi hai chieu ----------
        try (Cli A = new Cli("bi_a"); Cli B = new Cli("bi_b")) {
            A.register(); B.register(); A.login(); B.login();
            int N = 200;
            long t0 = System.nanoTime();
            Thread ta = new Thread(() -> { for (int i = 0; i < N; i++) A.send("SEND_PRIVATE_MESSAGE", obj("receiver", "bi_b", "content", "a" + i)); });
            Thread tb = new Thread(() -> { for (int i = 0; i < N; i++) B.send("SEND_PRIVATE_MESSAGE", obj("receiver", "bi_a", "content", "b" + i)); });
            ta.start(); tb.start(); ta.join(); tb.join();
            int gotB = 0, gotA = 0; boolean orderOk = true; int lastB = -1, lastA = -1;
            for (int i = 0; i < N; i++) {
                JsonObject m = B.take("RECEIVE_PRIVATE_MESSAGE", 15000); if (m == null) break; gotB++;
                int v = Integer.parseInt(m.getAsJsonObject("data").get("content").getAsString().substring(1)); if (v != lastB + 1) orderOk = false; lastB = v;
            }
            for (int i = 0; i < N; i++) {
                JsonObject m = A.take("RECEIVE_PRIVATE_MESSAGE", 15000); if (m == null) break; gotA++;
                int v = Integer.parseInt(m.getAsJsonObject("data").get("content").getAsString().substring(1)); if (v != lastA + 1) orderOk = false; lastA = v;
            }
            long ms = (System.nanoTime() - t0) / 1_000_000;
            // lich su sau trao doi
            A.send("GET_CHAT_HISTORY", obj("targetUser", "bi_b"));
            JsonObject h = A.take("GET_CHAT_HISTORY_RESPONSE", 10000);
            int hist = h == null ? -1 : h.getAsJsonObject("data").getAsJsonArray("messages").size();
            row("T3", "Hai client gui dong thoi moi ben " + N + " tin", (gotA == N && gotB == N && orderOk && hist == Math.min(50, 2 * N)) ? "DAT" : "LOI",
                    "A nhan " + gotA + "/" + N + ", B nhan " + gotB + "/" + N + ", thu tu " + (orderOk ? "dung" : "sai") + ", tong " + ms + " ms; lich su tra ve " + hist + " tin (gioi han 50)");
        }

        // ---------- T4: truyen tep nhieu kich thuoc ----------
        try (Cli A = new Cli("ft_a"); Cli B = new Cli("ft_b")) {
            A.register(); B.register(); A.login(); B.login();
            Random rnd = new Random(7);
            for (int kb : new int[] { 1, 100, 1024, 5 * 1024, 10 * 1024, 20 * 1024 }) {
                byte[] data = new byte[kb * 1024]; rnd.nextBytes(data);
                JsonObject d = obj("receiver", "ft_b", "fileName", "test_" + kb + "kb.bin", "fileType", "FILE", "base64Data", Base64.getEncoder().encodeToString(data));
                long t0 = System.nanoTime();
                A.send("SEND_FILE", d);
                JsonObject up = A.take("SEND_FILE_RESPONSE", 60000);
                long upMs = (System.nanoTime() - t0) / 1_000_000;
                boolean upOk = up != null && up.get("status").getAsString().equals("SUCCESS");
                JsonObject notif = upOk ? B.take("RECEIVE_NOTIFICATION", 10000) : null;
                int fileId = upOk ? up.getAsJsonObject("data").get("fileId").getAsInt() : -1;
                long t1 = System.nanoTime();
                B.send("DOWNLOAD_FILE", fileIdObj(fileId));
                JsonObject dl = B.take("DOWNLOAD_FILE_RESPONSE", 60000);
                long dlMs = (System.nanoTime() - t1) / 1_000_000;
                boolean same = false;
                if (dl != null && dl.get("status").getAsString().equals("SUCCESS"))
                    same = sha(Base64.getDecoder().decode(dl.getAsJsonObject("data").get("base64Data").getAsString())).equals(sha(data));
                String size = kb >= 1024 ? (kb / 1024) + " MB" : kb + " KB";
                row("T4." + kb, "Truyen tep " + size, (upOk && notif != null && same) ? "DAT" : "LOI",
                        "tai len " + upMs + " ms, tai ve " + dlMs + " ms, thong bao " + (notif != null ? "co" : "khong") + ", noi dung " + (same ? "khop (SHA-256)" : "KHONG khop"));
            }
        }

        // ---------- T5: loi va gian doan ----------
        try (Cli A = new Cli("er_a"); Cli B = new Cli("er_b")) {
            A.register(); B.register();
            // chua dang nhap
            A.send("SEND_PRIVATE_MESSAGE", obj("receiver", "er_b", "content", "x"));
            JsonObject r1 = A.take("ERROR", 5000);
            row("T5.1", "Gui tin khi chua dang nhap", (r1 != null && r1.get("status").getAsString().equals("FAILED")) ? "DAT" : "LOI", "server tra ERROR/FAILED");
            A.login(); B.login();
            // action la
            A.send("DO_SOMETHING", null);
            JsonObject r2 = A.take("ERROR", 5000);
            row("T5.2", "Gui action khong ho tro", (r2 != null && r2.get("status").getAsString().equals("FAILED")) ? "DAT" : "LOI", "server tra ERROR/FAILED");
            // JSON sai dinh dang
            A.w.println("{day khong phai json");
            A.send("GET_USER_LIST", null);
            JsonObject r3 = A.take("GET_USER_LIST_RESPONSE", 5000);
            row("T5.3", "Gui dong JSON sai dinh dang, sau do gui yeu cau hop le", r3 != null ? "DAT" : "LOI", "server bo qua goi loi (khong phan hoi) va van phuc vu yeu cau tiep theo tren cung ket noi");
            // base64 sai
            A.send("SEND_FILE", obj("receiver", "er_b", "fileName", "x.bin", "base64Data", "@@@khong-phai-base64"));
            JsonObject r4 = A.take("SEND_FILE_RESPONSE", 5000);
            row("T5.4", "Gui tep co du lieu Base64 khong hop le", (r4 != null && r4.get("status").getAsString().equals("FAILED")) ? "DAT" : "LOI", "server tra FAILED, khong sap");
            // tai tep khong ton tai
            B.send("DOWNLOAD_FILE", fileIdObj(999999));
            JsonObject r5 = B.take("DOWNLOAD_FILE_RESPONSE", 5000);
            row("T5.5", "Tai tep khong ton tai", (r5 != null && r5.get("status").getAsString().equals("FAILED")) ? "DAT" : "LOI", "server tra FAILED");
            // dang nhap sai mat khau
            try (Cli C = new Cli("er_a")) {
                C.send("LOGIN", obj("username", "er_a", "password", "sai"));
                JsonObject r6 = C.take("LOGIN_RESPONSE", 5000);
                row("T5.6", "Dang nhap sai mat khau", (r6 != null && r6.get("status").getAsString().equals("FAILED")) ? "DAT" : "LOI", "server tra FAILED");
            }
            // dong dot ngot khi dang online: B dong socket, A gui tin cho B (offline) roi B dang nhap lai
            B.close();
            JsonObject off = A.takeWhere("USER_STATUS_CHANGE", "username", "er_b", 5000);
            A.send("SEND_PRIVATE_MESSAGE", obj("receiver", "er_b", "content", "tin khi B offline"));
            JsonObject sent = A.take("SEND_PRIVATE_MESSAGE_RESPONSE", 5000);
            try (Cli B2 = new Cli("er_b")) {
                B2.login();
                B2.send("GET_CHAT_HISTORY", obj("targetUser", "er_a"));
                JsonObject h = B2.take("GET_CHAT_HISTORY_RESPONSE", 5000);
                boolean found = h != null && h.toString().contains("tin khi B offline");
                row("T5.7", "Mat ket noi dot ngot, gui tin khi nguoi nhan offline, nguoi nhan dang nhap lai", (off != null && sent != null && found) ? "DAT" : "LOI",
                        "A nhan thong bao OFFLINE; tin duoc luu va B xem lai duoc trong lich su");
            }
        }

        // ---------- T6: chat nhom ----------
        try (Cli A = new Cli("gr_a"); Cli B = new Cli("gr_b"); Cli C = new Cli("gr_c")) {
            A.register(); B.register(); C.register(); A.login(); B.login(); C.login();
            JsonObject d = obj("name", "Nhom test"); JsonArray m = new JsonArray(); m.add("gr_b"); m.add("gr_c"); d.add("members", m);
            A.send("CREATE_GROUP", d);
            JsonObject cr = A.take("CREATE_GROUP_RESPONSE", 5000);
            int gid = cr.getAsJsonObject("data").get("groupId").getAsInt();
            JsonObject upd = B.take("GROUP_UPDATED", 5000);
            JsonObject gm = new JsonObject(); gm.addProperty("groupId", gid); gm.addProperty("content", "xin chao nhom");
            long t0 = System.nanoTime(); A.send("SEND_GROUP_MESSAGE", gm);
            JsonObject rb = B.take("RECEIVE_GROUP_MESSAGE", 5000), rc = C.take("RECEIVE_GROUP_MESSAGE", 5000);
            long ms = (System.nanoTime() - t0) / 1_000_000;
            boolean selfNo = A.take("RECEIVE_GROUP_MESSAGE", 300) == null;
            row("T6", "Tao nhom 3 nguoi, gui tin nhom", (upd != null && rb != null && rc != null && selfNo) ? "DAT" : "LOI",
                    "2 thanh vien khac deu nhan trong " + ms + " ms, nguoi gui khong nhan lai ban sao");
        }
        System.out.println("DONE");
        System.exit(0);
    }

    static JsonObject fileIdObj(int id) { JsonObject o = new JsonObject(); o.addProperty("fileId", id); return o; }
}
