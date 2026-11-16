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
        // Gửi tin chữ
        view.getInputPanel().setSendMessageListener(content -> {
            sendPrivateMessage(content);
        });

        // Gửi tệp (ảnh, video, tài liệu)
        view.getInputPanel().setSendFileListener((file, fileType) -> {
            String receiver = view.getCurrentPartnerUsername();
            if (receiver != null && !receiver.trim().isEmpty()) {
                FileClientManager.getInstance().sendFile(writer, receiver, file, fileType, 0, "");
            }
        });

        // Gửi ghi âm
        view.getInputPanel().setSendVoiceListener((wavBytes, durationSeconds) -> {
            String receiver = view.getCurrentPartnerUsername();
            if (receiver != null && !receiver.trim().isEmpty()) {
                String fileName = "voice_" + System.currentTimeMillis() + ".wav";
                FileClientManager.getInstance().sendBytes(writer, receiver, wavBytes, fileName, "AUDIO", durationSeconds, "");
            }
        });
    }

    // Mở hội thoại với người được chọn trong danh bạ
    public void openChatWith(ChatPartner partner) {
        if (partner == null) {
            view.showPlaceholder();
            return;
        }

        // Chuyển sang khung chat
        view.showChat(partner);
        view.getMessageListPanel().clearMessages();

        // Tải lịch sử
        JsonObject req = new JsonObject();
        req.addProperty("action", "GET_CHAT_HISTORY");
        JsonObject data = new JsonObject();
        data.addProperty("targetUser", partner.getUsername());
        data.addProperty("limit", 50);
        req.add("data", data);

        writer.println(gson.toJson(req));
    }

    // Gửi tin nhắn lên Server
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

    // GET_CHAT_HISTORY_RESPONSE
    public void onChatHistoryReceived(JsonObject resData) {
        if (resData == null) return;

        String targetUser = resData.has("targetUser") ? resData.get("targetUser").getAsString() : "";
        String activeUser = view.getCurrentPartnerUsername();

        // Trong lúc chờ, người dùng có thể đã chuyển sang hội thoại khác
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

    // Server xác nhận đã lưu thì mới hiện tin của mình lên khung chat
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

    // RECEIVE_PRIVATE_MESSAGE: người khác vừa gửi tin tới
    public void onMessageReceived(JsonObject msgData) {
        if (msgData == null) return;

        ChatMessage msg = ChatMessage.fromJson(msgData, currentUsername);
        String sender = msg.getSender();
        String activeUser = view.getCurrentPartnerUsername();

        // Đang mở đúng hội thoại thì hiện luôn
        if (activeUser != null && activeUser.equalsIgnoreCase(sender)) {
            SwingUtilities.invokeLater(() -> {
                view.getMessageListPanel().addMessage(msg);
            });

            // Báo Server là đã đọc
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

    // Người đang chat online/offline thì cập nhật header
    public void onPartnerStatusChanged(String username, String status) {
        String activeUser = view.getCurrentPartnerUsername();
        if (activeUser != null && activeUser.equalsIgnoreCase(username)) {
            SwingUtilities.invokeLater(() -> {
                view.getHeaderPanel().updateStatus(status);
            });
        }
    }
}
