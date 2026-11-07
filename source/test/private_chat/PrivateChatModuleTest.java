package private_chat;

import com.google.gson.JsonObject;
import private_chat.dao.ConversationDAO;
import private_chat.dao.MessageDAO;
import private_chat.handler.PrivateChatHandler;
import private_chat.model.Conversation;
import private_chat.model.Message;
import testutil.TestSupport;
import testutil.TestSupport.Capture;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static testutil.TestSupport.check;
import static testutil.TestSupport.isStatus;
import static testutil.TestSupport.request;

/** Kiểm thử Module 2 (Private Chat): ConversationDAO, MessageDAO, PrivateChatHandler. */
public class PrivateChatModuleTest {

    public static void main(String[] args) throws Exception {
        TestSupport.initTempDb();
        run();
        System.exit(TestSupport.summary());
    }

    public static void run() {
        TestSupport.section("MODULE 2 - PRIVATE CHAT");
        TestSupport.registerUsers("pc_ann", "pc_bao", "pc_chi");

        testConversationDao();
        testMessageDao();
        testHandlerValidation();
        testHandlerSendOnlineReceiver();
        testHandlerSendOfflineReceiver();
        testHandlerHistoryAndRead();
        testIsPrivateChatAction();
    }

    // ---------- ConversationDAO ----------
    static void testConversationDao() {
        Conversation c1 = ConversationDAO.getOrCreateConversation("pc_ann", "pc_bao");
        check("conversation: tạo mới thành công", c1 != null && c1.getId() > 0);

        Conversation c2 = ConversationDAO.getOrCreateConversation("PC_BAO", "pc_ann");
        check("conversation: đảo thứ tự/hoa thường vẫn trả về cùng 1 đoạn chat",
                c2 != null && c2.getId() == c1.getId());

        check("conversation: username được chuẩn hóa theo alphabet (user1 <= user2)",
                c1.getUser1Username().compareToIgnoreCase(c1.getUser2Username()) <= 0);
        check("conversation: tự chat với chính mình -> null",
                ConversationDAO.getOrCreateConversation("pc_ann", "PC_ANN") == null);
        check("conversation: tên rỗng/null -> null",
                ConversationDAO.getOrCreateConversation("", "pc_ann") == null
                        && ConversationDAO.getOrCreateConversation(null, "pc_ann") == null);
        check("conversation: getConversationById tìm đúng", ConversationDAO.getConversationById(c1.getId()) != null);
        check("conversation: getConversationById không tồn tại -> null", ConversationDAO.getConversationById(999999) == null);

        ConversationDAO.getOrCreateConversation("pc_ann", "pc_chi");
        check("conversation: getUserConversations trả về mọi đoạn chat của user",
                ConversationDAO.getUserConversations("pc_ann").size() == 2);
        check("conversation: getOtherUser trả về đối phương", "pc_bao".equals(c1.getOtherUser("pc_ann")));
    }

    // ---------- MessageDAO ----------
    static void testMessageDao() {
        Conversation conv = ConversationDAO.getOrCreateConversation("pc_bao", "pc_chi");
        Message m1 = MessageDAO.saveMessage(conv.getId(), "pc_bao", "xin chào");
        check("message: lưu thành công, có id và thời gian gửi",
                m1 != null && m1.getId() > 0 && m1.getSentAt() != null && "xin chào".equals(m1.getContent()));
        check("message: tin mới mặc định chưa đọc", !m1.isRead());

        MessageDAO.saveMessage(conv.getId(), "pc_chi", "chào bạn");
        MessageDAO.saveMessage(conv.getId(), "pc_bao", "tin 3");
        MessageDAO.saveMessage(conv.getId(), "pc_chi", "tin 4");

        List<Message> last2 = MessageDAO.getMessagesByConversationId(conv.getId(), 2);
        check("message: limit=2 lấy 2 tin mới nhất theo thứ tự cũ -> mới",
                last2.size() == 2 && "tin 3".equals(last2.get(0).getContent())
                        && "tin 4".equals(last2.get(1).getContent()));
        check("message: getMessagesBetweenUsers lấy đủ 4 tin",
                MessageDAO.getMessagesBetweenUsers("pc_chi", "pc_bao", 50).size() == 4);

        check("message: unread của pc_chi = 2 (chỉ tính tin do pc_bao gửi)",
                MessageDAO.getUnreadCount(conv.getId(), "pc_chi") == 2);
        check("message: markMessagesAsRead trả về số tin được đánh dấu",
                MessageDAO.markMessagesAsRead(conv.getId(), "pc_chi") == 2);
        check("message: sau khi đọc, unread = 0", MessageDAO.getUnreadCount(conv.getId(), "pc_chi") == 0);
        check("message: tin do chính mình gửi không bị đánh dấu thay người khác",
                MessageDAO.getUnreadCount(conv.getId(), "pc_bao") == 2);
        check("message: getMessageById không tồn tại -> null", MessageDAO.getMessageById(999999) == null);
    }

    // ---------- PrivateChatHandler ----------
    static void testHandlerValidation() {
        Map<String, java.io.PrintWriter> online = new HashMap<>();
        Capture out = new Capture();

        PrivateChatHandler.handlePrivateChatAction(
                request("SEND_PRIVATE_MESSAGE", "{\"receiver\":\"pc_bao\",\"content\":\"hi\"}"), out.writer, null, online);
        check("handler: chưa đăng nhập -> ERROR/FAILED", isStatus(out.last(), "ERROR", "FAILED"));

        out.clear();
        PrivateChatHandler.handlePrivateChatAction(
                request("SEND_PRIVATE_MESSAGE", "{\"receiver\":\"pc_bao\"}"), out.writer, "pc_ann", online);
        check("handler SEND: thiếu content -> FAILED", isStatus(out.last(), "SEND_PRIVATE_MESSAGE_RESPONSE", "FAILED"));

        out.clear();
        PrivateChatHandler.handlePrivateChatAction(
                request("SEND_PRIVATE_MESSAGE", "{\"receiver\":\"pc_bao\",\"content\":\"   \"}"), out.writer, "pc_ann", online);
        check("handler SEND: nội dung toàn khoảng trắng -> FAILED", isStatus(out.last(), "SEND_PRIVATE_MESSAGE_RESPONSE", "FAILED"));

        out.clear();
        PrivateChatHandler.handlePrivateChatAction(
                request("SEND_PRIVATE_MESSAGE", "{\"receiver\":\"PC_ANN\",\"content\":\"tự gửi\"}"), out.writer, "pc_ann", online);
        check("handler SEND: tự gửi cho chính mình -> FAILED", isStatus(out.last(), "SEND_PRIVATE_MESSAGE_RESPONSE", "FAILED"));

        out.clear();
        PrivateChatHandler.handlePrivateChatAction(request("UNKNOWN_CHAT", null), out.writer, "pc_ann", online);
        check("handler: action lạ -> ERROR/FAILED", isStatus(out.last(), "ERROR", "FAILED"));
    }

    static void testHandlerSendOnlineReceiver() {
        Capture sender = new Capture();
        Capture receiver = new Capture();
        Map<String, java.io.PrintWriter> online = new HashMap<>();
        online.put("pc_bao", receiver.writer);

        PrivateChatHandler.handlePrivateChatAction(
                request("SEND_PRIVATE_MESSAGE", "{\"receiver\":\"pc_bao\",\"content\":\"hello bao\"}"),
                sender.writer, "pc_ann", online);

        JsonObject res = sender.last();
        check("handler SEND: người gửi nhận SEND_PRIVATE_MESSAGE_RESPONSE SUCCESS",
                isStatus(res, "SEND_PRIVATE_MESSAGE_RESPONSE", "SUCCESS"));
        JsonObject data = res.getAsJsonObject("data");
        check("handler SEND: phản hồi có id, conversationId, sender, receiver, content",
                data.get("id").getAsInt() > 0 && data.get("conversationId").getAsInt() > 0
                        && "pc_ann".equals(data.get("sender").getAsString())
                        && "pc_bao".equals(data.get("receiver").getAsString())
                        && "hello bao".equals(data.get("content").getAsString()));

        JsonObject recv = receiver.find("RECEIVE_PRIVATE_MESSAGE");
        check("handler SEND: người nhận online được chuyển tiếp RECEIVE_PRIVATE_MESSAGE realtime",
                recv != null && "hello bao".equals(recv.getAsJsonObject("data").get("content").getAsString()));

        JsonObject notif = receiver.find("RECEIVE_NOTIFICATION");
        check("handler SEND: người nhận online được gửi RECEIVE_NOTIFICATION (TEXT) kèm họ tên người gửi",
                notif != null && "TEXT".equals(notif.getAsJsonObject("data").get("type").getAsString())
                        && notif.getAsJsonObject("data").get("message").getAsString().contains("Name of pc_ann")
                        && "pc_ann".equals(notif.getAsJsonObject("data").get("sender").getAsString()));

        // Tin dài: thông báo bị cắt còn 40 ký tự + "..."
        receiver.clear();
        String longText = "x".repeat(100);
        PrivateChatHandler.handlePrivateChatAction(
                request("SEND_PRIVATE_MESSAGE", "{\"receiver\":\"pc_bao\",\"content\":\"" + longText + "\"}"),
                sender.writer, "pc_ann", online);
        String shortMsg = receiver.find("RECEIVE_NOTIFICATION").getAsJsonObject("data").get("message").getAsString();
        check("handler SEND: thông báo cắt nội dung dài còn 40 ký tự + '...'",
                shortMsg.endsWith("x".repeat(40) + "...") && !shortMsg.contains("x".repeat(41)));
    }

    static void testHandlerSendOfflineReceiver() {
        Capture sender = new Capture();
        Map<String, java.io.PrintWriter> online = new HashMap<>(); // pc_chi không online

        PrivateChatHandler.handlePrivateChatAction(
                request("SEND_PRIVATE_MESSAGE", "{\"receiver\":\"pc_chi\",\"content\":\"gửi offline\"}"),
                sender.writer, "pc_ann", online);
        check("handler SEND: người nhận offline vẫn gửi thành công",
                isStatus(sender.last(), "SEND_PRIVATE_MESSAGE_RESPONSE", "SUCCESS"));
        check("handler SEND: tin nhắn offline được lưu vào CSDL",
                MessageDAO.getMessagesBetweenUsers("pc_ann", "pc_chi", 50).stream()
                        .anyMatch(m -> "gửi offline".equals(m.getContent())));
    }

    static void testHandlerHistoryAndRead() {
        Map<String, java.io.PrintWriter> online = new HashMap<>();
        Capture ann = new Capture();
        Capture bao = new Capture();

        // pc_bao xem lịch sử với pc_ann: có tin do pc_ann gửi ở test trước (chưa đọc)
        Conversation conv = ConversationDAO.getOrCreateConversation("pc_ann", "pc_bao");
        int unreadBefore = MessageDAO.getUnreadCount(conv.getId(), "pc_bao");

        PrivateChatHandler.handlePrivateChatAction(
                request("GET_CHAT_HISTORY", "{\"targetUser\":\"pc_ann\",\"limit\":50}"), bao.writer, "pc_bao", online);
        JsonObject res = bao.last();
        check("handler HISTORY: SUCCESS và trả về danh sách messages",
                isStatus(res, "GET_CHAT_HISTORY_RESPONSE", "SUCCESS")
                        && res.getAsJsonObject("data").getAsJsonArray("messages").size() >= 2);
        check("handler HISTORY: có tin chưa đọc trước khi mở", unreadBefore >= 2);
        check("handler HISTORY: mở lịch sử tự đánh dấu đã đọc", MessageDAO.getUnreadCount(conv.getId(), "pc_bao") == 0);

        PrivateChatHandler.handlePrivateChatAction(request("GET_CHAT_HISTORY", "{}"), ann.writer, "pc_ann", online);
        check("handler HISTORY: thiếu targetUser -> FAILED", isStatus(ann.last(), "GET_CHAT_HISTORY_RESPONSE", "FAILED"));

        // MARK_MESSAGES_READ
        ann.clear();
        Conversation c2 = ConversationDAO.getOrCreateConversation("pc_ann", "pc_chi");
        PrivateChatHandler.handlePrivateChatAction(
                request("MARK_MESSAGES_READ", "{\"conversationId\":" + c2.getId() + "}"), ann.writer, "pc_chi", online);
        JsonObject mark = ann.last();
        check("handler MARK_READ: SUCCESS và markedCount >= 1 (tin offline của pc_ann)",
                isStatus(mark, "MARK_MESSAGES_READ_RESPONSE", "SUCCESS")
                        && mark.getAsJsonObject("data").get("markedCount").getAsInt() >= 1);

        ann.clear();
        PrivateChatHandler.handlePrivateChatAction(request("MARK_MESSAGES_READ", "{}"), ann.writer, "pc_chi", online);
        check("handler MARK_READ: thiếu conversationId -> không phản hồi", ann.isEmpty());
    }

    static void testIsPrivateChatAction() {
        check("isPrivateChatAction: nhận đúng 3 action",
                PrivateChatHandler.isPrivateChatAction("send_private_message")
                        && PrivateChatHandler.isPrivateChatAction("GET_CHAT_HISTORY")
                        && PrivateChatHandler.isPrivateChatAction("MARK_MESSAGES_READ"));
        check("isPrivateChatAction: từ chối action module khác", !PrivateChatHandler.isPrivateChatAction("LOGIN"));
    }
}
