package file_notification;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import file_notification.dao.FileAttachmentDAO;
import file_notification.handler.FileNotificationHandler;
import file_notification.model.FileAttachment;
import group_chat.dao.GroupDAO;
import group_chat.dao.GroupMessageDAO;
import private_chat.dao.ConversationDAO;
import private_chat.dao.MessageDAO;
import private_chat.model.Message;
import testutil.TestSupport;
import testutil.TestSupport.Capture;

import java.io.File;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static testutil.TestSupport.check;
import static testutil.TestSupport.isStatus;
import static testutil.TestSupport.request;

// Kiểm thử Module 4 (File & Notification): FileAttachmentDAO, FileNotificationHandler.
public class FileNotificationModuleTest {

    public static void main(String[] args) throws Exception {
        TestSupport.initTempDb();
        run();
        System.exit(TestSupport.summary());
    }

    public static void run() {
        TestSupport.section("MODULE 4 - FILE & NOTIFICATION");
        TestSupport.registerUsers("fn_ann", "fn_bao", "fn_cuong", "fn_out");

        testFileAttachmentDao();
        testHandlerValidation();
        testHandlerSendAndNotify();
        testHandlerNotificationTexts();
        testHandlerDownload();
        testHandlerGroupFile();
        testIsFileNotificationAction();
    }

    static JsonObject sendGroupFile(Capture sender, String user, String dataJson, Map<String, PrintWriter> online) {
        FileNotificationHandler.handleFileNotificationAction(request("SEND_GROUP_FILE", dataJson), sender.writer, user, online);
        return sender.last();
    }

    static String b64(String text) {
        return Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));
    }

    static JsonObject sendFile(Capture sender, String user, String dataJson, Map<String, PrintWriter> online) {
        FileNotificationHandler.handleFileNotificationAction(request("SEND_FILE", dataJson), sender.writer, user, online);
        return sender.last();
    }

    // FileAttachmentDAO 
    static void testFileAttachmentDao() {
        int convId = ConversationDAO.getOrCreateConversation("fn_ann", "fn_bao").getId();
        FileAttachment f = FileAttachmentDAO.saveFileAttachment(
                convId, 0, "fn_ann", "fn_bao", "bao_cao.pdf", "FILE", 2048, "/tmp/x/bao_cao.pdf");
        check("dao: lưu tệp trả về id > 0 và đúng metadata",
                f != null && f.getId() > 0 && "bao_cao.pdf".equals(f.getFileName()) && "FILE".equals(f.getFileType())
                        && f.getFileSize() == 2048 && "fn_ann".equals(f.getSenderUsername())
                        && "fn_bao".equals(f.getReceiverUsername()) && f.getCreatedAt() != null);

        FileAttachmentDAO.updateMessageId(f.getId(), 77);
        check("dao: updateMessageId gắn tệp với tin nhắn", FileAttachmentDAO.getFileAttachmentById(f.getId()).getMessageId() == 77);
        check("dao: getFileAttachmentById không tồn tại -> null", FileAttachmentDAO.getFileAttachmentById(999999) == null);
    }

    // Handler: kiểm tra đầu vào 
    static void testHandlerValidation() {
        Map<String, PrintWriter> online = new HashMap<>();
        Capture out = new Capture();

        FileNotificationHandler.handleFileNotificationAction(
                request("SEND_FILE", "{\"receiver\":\"fn_bao\",\"fileName\":\"a.txt\",\"base64Data\":\"" + b64("x") + "\"}"),
                out.writer, null, online);
        check("handler: chưa đăng nhập -> ERROR/FAILED", isStatus(out.last(), "ERROR", "FAILED"));

        check("handler SEND: thiếu base64Data -> FAILED",
                isStatus(sendFile(new Capture(), "fn_ann", "{\"receiver\":\"fn_bao\",\"fileName\":\"a.txt\"}", online),
                        "SEND_FILE_RESPONSE", "FAILED"));
        check("handler SEND: dữ liệu rỗng -> FAILED",
                isStatus(sendFile(new Capture(), "fn_ann",
                        "{\"receiver\":\"fn_bao\",\"fileName\":\"a.txt\",\"base64Data\":\"\"}", online),
                        "SEND_FILE_RESPONSE", "FAILED"));
        check("handler SEND: tự gửi file cho chính mình -> FAILED",
                isStatus(sendFile(new Capture(), "fn_ann",
                        "{\"receiver\":\"FN_ANN\",\"fileName\":\"a.txt\",\"base64Data\":\"" + b64("x") + "\"}", online),
                        "SEND_FILE_RESPONSE", "FAILED"));
        check("handler SEND: Base64 không hợp lệ -> FAILED (không làm sập server)",
                isStatus(sendFile(new Capture(), "fn_ann",
                        "{\"receiver\":\"fn_bao\",\"fileName\":\"a.txt\",\"base64Data\":\"@@không-phải-base64@@\"}", online),
                        "SEND_FILE_RESPONSE", "FAILED"));

        out.clear();
        FileNotificationHandler.handleFileNotificationAction(request("UNKNOWN_FILE", null), out.writer, "fn_ann", online);
        check("handler: action lạ -> ERROR/FAILED", isStatus(out.last(), "ERROR", "FAILED"));
    }

    // Handler: gửi file + thông báo realtime 
    static void testHandlerSendAndNotify() {
        Capture sender = new Capture();
        Capture receiver = new Capture();
        Map<String, PrintWriter> online = new HashMap<>();
        online.put("fn_bao", receiver.writer);

        JsonObject res = sendFile(sender, "fn_ann",
                "{\"receiver\":\"fn_bao\",\"fileName\":\"báo cáo (1).pdf\",\"fileType\":\"file\",\"base64Data\":\""
                        + b64("noi dung file") + "\",\"caption\":\"xem giúp mình\"}",
                online);
        check("handler SEND: gửi tệp thành công", isStatus(res, "SEND_FILE_RESPONSE", "SUCCESS"));

        JsonObject data = res.getAsJsonObject("data");
        check("handler SEND: loại tệp được chuẩn hóa thành chữ hoa, có fileId và kích thước đúng",
                "FILE".equals(data.get("fileType").getAsString()) && data.get("fileId").getAsInt() > 0
                        && data.get("fileSize").getAsLong() == "noi dung file".getBytes(StandardCharsets.UTF_8).length);

        FileAttachment att = FileAttachmentDAO.getFileAttachmentById(data.get("fileId").getAsInt());
        File stored = new File(att.getStoredPath());
        check("handler SEND: tệp được ghi ra đĩa", stored.exists() && stored.length() == att.getFileSize());
        check("handler SEND: tên tệp lưu trữ đã được làm sạch ký tự đặc biệt/dấu cách",
                stored.getName().matches("[a-zA-Z0-9._-]+"));
        check("handler SEND: tên gốc vẫn được giữ trong CSDL", "báo cáo (1).pdf".equals(att.getFileName()));

        Message msg = MessageDAO.getMessageById(data.get("id").getAsInt());
        JsonObject media = msg != null ? JsonParser.parseString(msg.getContent()).getAsJsonObject() : new JsonObject();
        check("handler SEND: tin nhắn lưu dạng JSON media (isMedia, fileId, caption)",
                media.has("isMedia") && media.get("isMedia").getAsBoolean()
                        && media.get("fileId").getAsInt() == att.getId()
                        && "xem giúp mình".equals(media.get("caption").getAsString()));
        check("handler SEND: messageId của tệp được cập nhật sau khi lưu tin nhắn", att.getMessageId() == msg.getId());

        check("handler SEND: người nhận online nhận RECEIVE_PRIVATE_MESSAGE realtime",
                receiver.find("RECEIVE_PRIVATE_MESSAGE") != null);
        JsonObject notif = receiver.find("RECEIVE_NOTIFICATION");
        check("handler SEND: người nhận online nhận RECEIVE_NOTIFICATION (type FILE, kèm tên và fileId)",
                notif != null && "FILE".equals(notif.getAsJsonObject("data").get("type").getAsString())
                        && "fn_ann".equals(notif.getAsJsonObject("data").get("sender").getAsString())
                        && notif.getAsJsonObject("data").get("fileId").getAsInt() == att.getId());

        // Người nhận offline: vẫn gửi thành công, không có gói realtime
        Capture offSender = new Capture();
        JsonObject off = sendFile(offSender, "fn_bao",
                "{\"receiver\":\"fn_ann\",\"fileName\":\"b.txt\",\"base64Data\":\"" + b64("b") + "\"}", new HashMap<>());
        check("handler SEND: người nhận offline vẫn lưu và phản hồi SUCCESS", isStatus(off, "SEND_FILE_RESPONSE", "SUCCESS"));
        check("handler SEND: loại tệp mặc định là FILE khi không truyền fileType",
                "FILE".equals(off.getAsJsonObject("data").get("fileType").getAsString()));
    }

    // Handler: nội dung thông báo theo loại tệp 
    static void testHandlerNotificationTexts() {
        String[][] cases = {
                { "IMAGE", "hình ảnh" },
                { "VIDEO", "video" },
                { "AUDIO", "đoạn ghi âm" },
                { "FILE", "tệp tin" } };
        for (String[] c : cases) {
            Capture receiver = new Capture();
            Map<String, PrintWriter> online = new HashMap<>();
            online.put("fn_bao", receiver.writer);
            sendFile(new Capture(), "fn_ann",
                    "{\"receiver\":\"fn_bao\",\"fileName\":\"f.bin\",\"fileType\":\"" + c[0].toLowerCase()
                            + "\",\"duration\":7,\"base64Data\":\"" + b64("d") + "\"}", online);
            JsonObject notif = receiver.find("RECEIVE_NOTIFICATION");
            String message = notif != null ? notif.getAsJsonObject("data").get("message").getAsString() : "";
            check("notification: loại " + c[0] + " -> nội dung chứa '" + c[1] + "' và họ tên người gửi",
                    message.contains(c[1]) && message.contains("Name of fn_ann")
                            && c[0].equals(notif.getAsJsonObject("data").get("fileType").getAsString()));
            if ("AUDIO".equals(c[0])) {
                check("notification: AUDIO hiển thị thời lượng (7s)", message.contains("7s"));
            }
        }
    }

    // Handler: tải file 
    static void testHandlerDownload() {
        Map<String, PrintWriter> online = new HashMap<>();
        String original = "Dữ liệu tải về phải giống hệt bản gốc";
        JsonObject up = sendFile(new Capture(), "fn_ann",
                "{\"receiver\":\"fn_bao\",\"fileName\":\"dl.txt\",\"base64Data\":\"" + b64(original) + "\"}", online);
        int fileId = up.getAsJsonObject("data").get("fileId").getAsInt();

        Capture dl = new Capture();
        FileNotificationHandler.handleFileNotificationAction(
                request("DOWNLOAD_FILE", "{\"fileId\":" + fileId + "}"), dl.writer, "fn_bao", online);
        JsonObject res = dl.last();
        check("handler DOWNLOAD: thành công", isStatus(res, "DOWNLOAD_FILE_RESPONSE", "SUCCESS"));
        String decoded = new String(Base64.getDecoder().decode(res.getAsJsonObject("data").get("base64Data").getAsString()),
                StandardCharsets.UTF_8);
        check("handler DOWNLOAD: nội dung tải về trùng khớp bản gốc (gửi -> tải khứ hồi)", original.equals(decoded));
        check("handler DOWNLOAD: trả về đủ tên, loại, kích thước",
                "dl.txt".equals(res.getAsJsonObject("data").get("fileName").getAsString())
                        && res.getAsJsonObject("data").has("fileType") && res.getAsJsonObject("data").has("fileSize"));

        dl.clear();
        FileNotificationHandler.handleFileNotificationAction(request("DOWNLOAD_FILE", "{}"), dl.writer, "fn_bao", online);
        check("handler DOWNLOAD: thiếu fileId -> FAILED", isStatus(dl.last(), "DOWNLOAD_FILE_RESPONSE", "FAILED"));

        dl.clear();
        FileNotificationHandler.handleFileNotificationAction(
                request("DOWNLOAD_FILE", "{\"fileId\":999999}"), dl.writer, "fn_bao", online);
        check("handler DOWNLOAD: fileId không tồn tại -> FAILED", isStatus(dl.last(), "DOWNLOAD_FILE_RESPONSE", "FAILED"));

        // Tệp bị xóa khỏi đĩa nhưng vẫn còn bản ghi trong CSDL
        new File(FileAttachmentDAO.getFileAttachmentById(fileId).getStoredPath()).delete();
        dl.clear();
        FileNotificationHandler.handleFileNotificationAction(
                request("DOWNLOAD_FILE", "{\"fileId\":" + fileId + "}"), dl.writer, "fn_bao", online);
        check("handler DOWNLOAD: tệp đã mất trên đĩa -> FAILED", isStatus(dl.last(), "DOWNLOAD_FILE_RESPONSE", "FAILED"));
    }

    // Handler: gửi tệp / ghi âm trong nhóm 
    static void testHandlerGroupFile() {
        int gid = GroupDAO.createGroup("Nhóm gửi tệp", "fn_ann", List.of("fn_bao", "fn_cuong"));
        Map<String, PrintWriter> online = new HashMap<>();
        Capture bao = new Capture();
        Capture outsider = new Capture();
        online.put("fn_bao", bao.writer);
        online.put("fn_out", outsider.writer);

        check("group file: thiếu groupId -> FAILED",
                isStatus(sendGroupFile(new Capture(), "fn_ann",
                        "{\"fileName\":\"a.wav\",\"base64Data\":\"" + b64("x") + "\"}", online),
                        "SEND_GROUP_FILE_RESPONSE", "FAILED"));
        check("group file: người ngoài nhóm gửi -> FAILED",
                isStatus(sendGroupFile(new Capture(), "fn_out",
                        "{\"groupId\":" + gid + ",\"fileName\":\"a.wav\",\"base64Data\":\"" + b64("x") + "\"}", online),
                        "SEND_GROUP_FILE_RESPONSE", "FAILED"));
        check("group file: Base64 không hợp lệ -> FAILED",
                isStatus(sendGroupFile(new Capture(), "fn_ann",
                        "{\"groupId\":" + gid + ",\"fileName\":\"a.wav\",\"base64Data\":\"@@sai@@\"}", online),
                        "SEND_GROUP_FILE_RESPONSE", "FAILED"));

        Capture ann = new Capture();
        online.put("fn_ann", ann.writer);
        String voice = "du lieu ghi am";
        JsonObject res = sendGroupFile(new Capture(), "fn_ann",
                "{\"groupId\":" + gid + ",\"fileName\":\"voice_1.wav\",\"fileType\":\"audio\",\"duration\":5,\"base64Data\":\""
                        + b64(voice) + "\"}", online);
        check("group file: thành viên gửi ghi âm -> SUCCESS", isStatus(res, "SEND_GROUP_FILE_RESPONSE", "SUCCESS"));

        JsonObject data = res.getAsJsonObject("data");
        JsonObject media = JsonParser.parseString(data.get("content").getAsString()).getAsJsonObject();
        check("group file: tin nhắn nhóm có content là JSON media (AUDIO, 5s, đúng fileId)",
                data.get("groupId").getAsInt() == gid && "fn_ann".equals(data.get("sender").getAsString())
                        && media.get("isMedia").getAsBoolean() && "AUDIO".equals(media.get("fileType").getAsString())
                        && media.get("duration").getAsInt() == 5
                        && media.get("fileId").getAsInt() == data.get("fileId").getAsInt());

        FileAttachment att = FileAttachmentDAO.getFileAttachmentById(data.get("fileId").getAsInt());
        File stored = new File(att.getStoredPath());
        check("group file: tệp ghi ra đĩa, gắn groupId và messageId của tin nhắn nhóm",
                stored.exists() && att.getGroupId() == gid && att.getMessageId() == data.get("id").getAsInt());

        JsonObject recv = bao.find("RECEIVE_GROUP_MESSAGE");
        check("group file: thành viên online nhận RECEIVE_GROUP_MESSAGE kèm tệp",
                recv != null && recv.getAsJsonObject("data").get("fileId").getAsInt() == att.getId());
        check("group file: người gửi và người ngoài nhóm không nhận gói đẩy",
                ann.find("RECEIVE_GROUP_MESSAGE") == null && outsider.find("RECEIVE_GROUP_MESSAGE") == null);
        check("group file: tin nhắn tệp nằm trong lịch sử nhóm",
                GroupMessageDAO.getHistory(gid, 50).stream().anyMatch(m -> m.getId() == data.get("id").getAsInt()));

        Capture dl = new Capture();
        FileNotificationHandler.handleFileNotificationAction(
                request("DOWNLOAD_FILE", "{\"fileId\":" + att.getId() + "}"), dl.writer, "fn_cuong", online);
        String decoded = isStatus(dl.last(), "DOWNLOAD_FILE_RESPONSE", "SUCCESS")
                ? new String(Base64.getDecoder().decode(dl.last().getAsJsonObject("data").get("base64Data").getAsString()),
                        StandardCharsets.UTF_8)
                : "";
        check("group file: thành viên tải về đúng nội dung gốc", voice.equals(decoded));

        dl.clear();
        FileNotificationHandler.handleFileNotificationAction(
                request("DOWNLOAD_FILE", "{\"fileId\":" + att.getId() + "}"), dl.writer, "fn_out", online);
        check("group file: người ngoài nhóm không tải được tệp của nhóm",
                isStatus(dl.last(), "DOWNLOAD_FILE_RESPONSE", "FAILED"));

        GroupDAO.deleteGroup(gid);
        check("group file: xóa nhóm thì xóa luôn bản ghi tệp và tệp trên đĩa",
                FileAttachmentDAO.getFileAttachmentById(att.getId()) == null && !stored.exists());
    }

    static void testIsFileNotificationAction() {
        check("isFileNotificationAction: nhận SEND_FILE, SEND_GROUP_FILE và DOWNLOAD_FILE",
                FileNotificationHandler.isFileNotificationAction("send_file")
                        && FileNotificationHandler.isFileNotificationAction("SEND_GROUP_FILE")
                        && FileNotificationHandler.isFileNotificationAction("DOWNLOAD_FILE"));
        check("isFileNotificationAction: từ chối action module khác",
                !FileNotificationHandler.isFileNotificationAction("SEND_PRIVATE_MESSAGE"));
    }
}
