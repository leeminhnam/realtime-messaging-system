package private_chat.controller;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import file_notification.controller.FileClientManager;
import private_chat.model.ChatMessage;
import private_chat.model.ChatPartner;
import private_chat.view.PrivateChatPanel;

import javax.swing.*;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

public class PrivateChatController {
    private static final Gson gson = new Gson();

    private final PrivateChatPanel view;
    private final PrintWriter writer;
    private final String currentUsername;

    public PrivateChatController(PrivateChatPanel view, PrintWriter writer, String currentUsername) {
        this.view = view;
        this.writer = writer;
        this.currentUsername = currentUsername;

        FileClientManager.getInstance().setWriter(writer);

        initEventHandlers();
    }

    private void initEventHandlers() {
        // 1. Lắng nghe sự kiện người dùng gửi tin nhắn chữ
        view.getInputPanel().setSendMessageListener(content -> {
            sendPrivateMessage(content);
        });

        // 2. Lắng nghe sự kiện người dùng chọn gửi tệp (ảnh, video, tài liệu) - Module 4
        view.getInputPanel().setSendFileListener((file, fileType) -> {
            String receiver = view.getCurrentPartnerUsername();
            if (receiver != null && !receiver.trim().isEmpty()) {
                FileClientManager.getInstance().sendFile(writer, receiver, file, fileType, 0, "");
            }
        });

        // 3. Lắng nghe sự kiện người dùng gửi đoạn ghi âm - Module 4
        view.getInputPanel().setSendVoiceListener((wavBytes, durationSeconds) -> {
            String receiver = view.getCurrentPartnerUsername();
            if (receiver != null && !receiver.trim().isEmpty()) {
                String fileName = "voice_" + System.currentTimeMillis() + ".wav";
                FileClientManager.getInstance().sendBytes(writer, receiver, wavBytes, fileName, "AUDIO", durationSeconds, "");
            }
        });
    }

    /**
     * Mở cuộc trò chuyện với một người dùng được chọn từ danh bạ.
     *
     * @param partner Thông tin đối tác trò chuyện
     */
    public void openChatWith(ChatPartner partner) {
        if (partner == null) {
            view.showPlaceholder();
            return;
        }

        // 1. Chuyển giao diện sang chế độ active chat
        view.showChat(partner);
        view.getMessageListPanel().clearMessages();

        // 2. Gửi yêu cầu lên Server để tải lịch sử chat cũ
        JsonObject req = new JsonObject();
        req.addProperty("action", "GET_CHAT_HISTORY");
        JsonObject data = new JsonObject();
        data.addProperty("targetUser", partner.getUsername());
        data.addProperty("limit", 50);
        req.add("data", data);

        writer.println(gson.toJson(req));
    }

    /**
     * Gửi tin nhắn mới lên Server.
     */
    public void sendPrivateMessage(String content) {
        String receiver = view.getCurrentPartnerUsername();
        if (receiver == null || receiver.trim().isEmpty() || content.trim().isEmpty()) {
            return;
        }

        JsonObject req = new JsonObject();
        req.addProperty("action", "SEND_PRIVATE_MESSAGE");
        JsonObject data = new JsonObject();
        data.addProperty("receiver", receiver);
        data.addProperty("content", content);
        req.add("data", data);

        writer.println(gson.toJson(req));
    }

    /**
     * Xử lý khi nhận được gói tin lịch sử chat từ Server (GET_CHAT_HISTORY_RESPONSE).
     */
    public void onChatHistoryReceived(JsonObject resData) {
        if (resData == null) return;

        String targetUser = resData.has("targetUser") ? resData.get("targetUser").getAsString() : "";
        String activeUser = view.getCurrentPartnerUsername();

        // Đảm bảo chỉ nạp tin nhắn nếu người dùng vẫn đang mở đúng khung chat đó
        if (activeUser != null && activeUser.equalsIgnoreCase(targetUser)) {
            List<ChatMessage> list = new ArrayList<>();
            if (resData.has("messages")) {
                JsonArray arr = resData.getAsJsonArray("messages");
                for (JsonElement elem : arr) {
                    ChatMessage msg = ChatMessage.fromJson(elem.getAsJsonObject(), currentUsername);
                    if (msg != null) {
                        list.add(msg);
                    }
                }
            }
            SwingUtilities.invokeLater(() -> {
                view.getMessageListPanel().setMessages(list);
            });
        }
    }

    /**
     * Xử lý phản hồi khi gửi tin nhắn hoặc gửi tệp thành công (SEND_PRIVATE_MESSAGE_RESPONSE / SEND_FILE_RESPONSE).
     * Hiển thị tin nhắn/media vừa gửi lên bong bóng bên phải.
     */
    public void onSendMessageSuccess(JsonObject msgData) {
        if (msgData == null) return;

        ChatMessage msg = ChatMessage.fromJson(msgData, currentUsername);
        String receiver = msg.getReceiver();
        String activeUser = view.getCurrentPartnerUsername();

        if (activeUser != null && activeUser.equalsIgnoreCase(receiver)) {
            SwingUtilities.invokeLater(() -> {
                view.getMessageListPanel().addMessage(msg);
            });
        }
    }

    /**
     * Xử lý khi nhận được tin nhắn thời gian thực do đối phương gửi đến (RECEIVE_PRIVATE_MESSAGE).
     */
    public void onMessageReceived(JsonObject msgData) {
        if (msgData == null) return;

        ChatMessage msg = ChatMessage.fromJson(msgData, currentUsername);
        String sender = msg.getSender();
        String activeUser = view.getCurrentPartnerUsername();

        // Nếu đang mở đúng khung chat của người gửi -> thêm bong bóng chat ngay lập tức
        if (activeUser != null && activeUser.equalsIgnoreCase(sender)) {
            SwingUtilities.invokeLater(() -> {
                view.getMessageListPanel().addMessage(msg);
            });

            // Gửi thông báo đã đọc về Server
            if (msg.getConversationId() > 0) {
                JsonObject readReq = new JsonObject();
                readReq.addProperty("action", "MARK_MESSAGES_READ");
                JsonObject data = new JsonObject();
                data.addProperty("conversationId", msg.getConversationId());
                readReq.add("data", data);
                writer.println(gson.toJson(readReq));
            }
        }
    }

    /**
     * Cập nhật trạng thái Online/Offline của người đang chat khi nhận được USER_STATUS_CHANGE.
     */
    public void onPartnerStatusChanged(String username, String status) {
        String activeUser = view.getCurrentPartnerUsername();
        if (activeUser != null && activeUser.equalsIgnoreCase(username)) {
            SwingUtilities.invokeLater(() -> {
                view.getHeaderPanel().updateStatus(status);
            });
        }
    }
}
