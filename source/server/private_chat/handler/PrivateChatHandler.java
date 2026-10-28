package private_chat.handler;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import private_chat.dao.ConversationDAO;
import private_chat.dao.MessageDAO;
import private_chat.model.Conversation;
import private_chat.model.Message;

import java.io.PrintWriter;
import java.util.List;
import java.util.Map;

public class PrivateChatHandler {
    private static final Gson gson = new Gson();

    /**
     * Kiểm tra xem một action có thuộc module Private Chat hay không.
     */
    public static boolean isPrivateChatAction(String action) {
        return "SEND_PRIVATE_MESSAGE".equalsIgnoreCase(action)
                || "GET_CHAT_HISTORY".equalsIgnoreCase(action)
                || "MARK_MESSAGES_READ".equalsIgnoreCase(action);
    }

    /**
     * Xử lý chính cho tất cả các hành động liên quan tới Module Private Chat (Nhắn tin 1-1).
     *
     * @param req              Đối tượng JSON yêu cầu từ Client
     * @param writer           Luồng ghi của Client gửi yêu cầu
     * @param currentUsername  Tên người dùng đang đăng nhập của Client này
     * @param onlineClients    Bảng ánh xạ các Client đang Online trên Server
     */
    public static void handlePrivateChatAction(JsonObject req, PrintWriter writer, String currentUsername,
            Map<String, PrintWriter> onlineClients) {

        if (currentUsername == null || currentUsername.trim().isEmpty()) {
            sendResponse(writer, "ERROR", "FAILED", "Bạn cần đăng nhập để thực hiện chức năng chat!", null);
            return;
        }

        String action = req.has("action") ? req.get("action").getAsString() : "";
        JsonObject data = req.has("data") && !req.get("data").isJsonNull() ? req.getAsJsonObject("data")
                : new JsonObject();

        switch (action) {
            case "SEND_PRIVATE_MESSAGE":
                handleSendMessage(data, writer, currentUsername, onlineClients);
                break;

            case "GET_CHAT_HISTORY":
                handleGetChatHistory(data, writer, currentUsername);
                break;

            case "MARK_MESSAGES_READ":
                handleMarkMessagesRead(data, writer, currentUsername);
                break;

            default:
                sendResponse(writer, "ERROR", "FAILED", "Hành động chat không hợp lệ!", null);
                break;
        }
    }

    /**
     * 1. Xử lý Gửi tin nhắn riêng 1-1:
     * - Lưu tin nhắn vào CSDL SQLite.
     * - Trả phản hồi thành công về cho người gửi.
     * - Nếu người nhận đang Online, chuyển tiếp tin nhắn thời gian thực (Realtime) qua Socket của họ.
     */
    private static void handleSendMessage(JsonObject data, PrintWriter senderWriter, String senderUsername,
            Map<String, PrintWriter> onlineClients) {

        if (!data.has("receiver") || !data.has("content")) {
            sendResponse(senderWriter, "SEND_PRIVATE_MESSAGE_RESPONSE", "FAILED", "Thiếu người nhận hoặc nội dung!", null);
            return;
        }

        String receiverUsername = data.get("receiver").getAsString().trim();
        String content = data.get("content").getAsString().trim();

        if (receiverUsername.isEmpty() || content.isEmpty()) {
            sendResponse(senderWriter, "SEND_PRIVATE_MESSAGE_RESPONSE", "FAILED", "Nội dung tin nhắn không được để trống!", null);
            return;
        }

        if (receiverUsername.equalsIgnoreCase(senderUsername)) {
            sendResponse(senderWriter, "SEND_PRIVATE_MESSAGE_RESPONSE", "FAILED", "Không thể tự gửi tin nhắn cho chính mình!", null);
            return;
        }

        System.out.println("[CHAT] Yêu cầu gửi tin: @" + senderUsername + " -> @" + receiverUsername + ": " + content);

        // 1. Tìm hoặc tạo mới đoạn chat giữa 2 người
        Conversation conv = ConversationDAO.getOrCreateConversation(senderUsername, receiverUsername);
        if (conv == null) {
            System.err.println("[CHAT LỖI] Không thể tạo conversation giữa @" + senderUsername + " và @" + receiverUsername);
            sendResponse(senderWriter, "SEND_PRIVATE_MESSAGE_RESPONSE", "FAILED", "Không thể khởi tạo cuộc hội thoại!", null);
            return;
        }

        // 2. Lưu tin nhắn vào CSDL
        Message savedMsg = MessageDAO.saveMessage(conv.getId(), senderUsername, content);
        if (savedMsg == null) {
            sendResponse(senderWriter, "SEND_PRIVATE_MESSAGE_RESPONSE", "FAILED", "Lỗi lưu tin nhắn vào CSDL!", null);
            return;
        }

        // Chuẩn bị dữ liệu gói tin nhắn
        JsonObject msgData = new JsonObject();
        msgData.addProperty("id", savedMsg.getId());
        msgData.addProperty("conversationId", savedMsg.getConversationId());
        msgData.addProperty("sender", senderUsername);
        msgData.addProperty("receiver", receiverUsername);
        msgData.addProperty("content", savedMsg.getContent());
        msgData.addProperty("sentAt", savedMsg.getSentAt());

        // 3. Phản hồi xác nhận gửi thành công về cho người gửi
        sendResponse(senderWriter, "SEND_PRIVATE_MESSAGE_RESPONSE", "SUCCESS", "Gửi tin nhắn thành công!", msgData);

        // 4. Nếu người nhận đang Online, chuyển tiếp tin nhắn ngay lập tức (Realtime)
        PrintWriter receiverWriter = onlineClients.get(receiverUsername);
        if (receiverWriter != null) {
            JsonObject receivePacket = new JsonObject();
            receivePacket.addProperty("action", "RECEIVE_PRIVATE_MESSAGE");
            receivePacket.addProperty("status", "SUCCESS");
            receivePacket.add("data", msgData);

            receiverWriter.println(gson.toJson(receivePacket));
            System.out.println("[REALTIME CHAT] Đã chuyển tiếp tin nhắn từ @" + senderUsername + " tới @" + receiverUsername);
        } else {
            System.out.println("[OFFLINE CHAT] Người nhận @" + receiverUsername + " đang offline. Đã lưu tin nhắn vào CSDL.");
        }
    }

    /**
     * 2. Xử lý Lấy lịch sử tin nhắn cuộc trò chuyện giữa 2 người:
     * - Tải danh sách N tin nhắn gần nhất.
     * - Tự động đánh dấu các tin nhắn chưa đọc mà đối phương gửi cho tôi thành ĐÃ ĐỌC.
     */
    private static void handleGetChatHistory(JsonObject data, PrintWriter writer, String currentUsername) {
        if (!data.has("targetUser")) {
            sendResponse(writer, "GET_CHAT_HISTORY_RESPONSE", "FAILED", "Thiếu thông tin người cần lấy tin nhắn!", null);
            return;
        }

        String targetUser = data.get("targetUser").getAsString().trim();
        int limit = data.has("limit") ? data.get("limit").getAsInt() : 50;

        Conversation conv = ConversationDAO.getOrCreateConversation(currentUsername, targetUser);
        if (conv == null) {
            sendResponse(writer, "GET_CHAT_HISTORY_RESPONSE", "FAILED", "Không thể tìm cuộc hội thoại!", null);
            return;
        }

        // Lấy danh sách tin nhắn cũ
        List<Message> history = MessageDAO.getMessagesByConversationId(conv.getId(), limit);

        // Đánh dấu đã đọc cho các tin nhắn đối phương gửi đến
        MessageDAO.markMessagesAsRead(conv.getId(), currentUsername);

        JsonObject resData = new JsonObject();
        resData.addProperty("conversationId", conv.getId());
        resData.addProperty("targetUser", targetUser);
        resData.add("messages", gson.toJsonTree(history));

        sendResponse(writer, "GET_CHAT_HISTORY_RESPONSE", "SUCCESS", "Tải lịch sử chat thành công!", resData);
    }

    /**
     * 3. Xử lý Đánh dấu tin nhắn đã đọc khi người dùng mở xem đoạn chat.
     */
    private static void handleMarkMessagesRead(JsonObject data, PrintWriter writer, String currentUsername) {
        if (!data.has("conversationId")) {
            return;
        }

        int conversationId = data.get("conversationId").getAsInt();
        int updatedCount = MessageDAO.markMessagesAsRead(conversationId, currentUsername);

        JsonObject resData = new JsonObject();
        resData.addProperty("conversationId", conversationId);
        resData.addProperty("markedCount", updatedCount);

        sendResponse(writer, "MARK_MESSAGES_READ_RESPONSE", "SUCCESS", "Đã đánh dấu đã đọc", resData);
    }

    /**
     * Tiện ích đóng gói JSON gửi về Client.
     */
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
}
