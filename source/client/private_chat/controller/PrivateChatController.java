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
import java.util.function.Consumer;
import java.util.function.BooleanSupplier;

public class PrivateChatController {
    private static final Gson gson = new Gson();

    private final PrivateChatPanel view;
    private final PrintWriter writer;
    private final String currentUsername;
    private Consumer<String> unreadListener = username -> {};
    private Consumer<String> chatReadListener = username -> {};
    private boolean chatVisible = true;
    private BooleanSupplier windowActive = () -> true;

    public void setWindowActiveSupplier(BooleanSupplier supplier) {
        windowActive = supplier;
    }

    private boolean isWatchingChat() {
        return chatVisible && windowActive.getAsBoolean();
    }

    // Khi quay lại cửa sổ, chỉ đọc nếu đang ở tab chat riêng
    public void onWindowActivated() {
        if (!isWatchingChat()) return;
        String partner = view.getCurrentPartnerUsername();
        if (partner != null) {
            chatReadListener.accept(partner);
            markMessagesRead(activeConversationId);
        }
    }
    private int activeConversationId;

    public void setUnreadListener(Consumer<String> listener) {
        unreadListener = listener;
    }

    public void setChatReadListener(Consumer<String> listener) {
        chatReadListener = listener;
    }

    // MainFrame gọi khi đổi tab; trở lại chat riêng thì đọc hội thoại đang hiện
    public void setChatVisible(boolean visible) {
        chatVisible = visible;
        onWindowActivated();
    }

    private void markMessagesRead(int conversationId) {
        if (conversationId <= 0) return;
        JsonObject req = new JsonObject();
        req.addProperty("action", "MARK_MESSAGES_READ");
        JsonObject data = new JsonObject();
        data.addProperty("conversationId", conversationId);
        req.add("data", data);
        writer.println(gson.toJson(req));
    }

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
        activeConversationId = 0;
        if (partner == null) {
            view.showPlaceholder();
            return;
        }

        // Chuyển sang khung chat
        view.showChat(partner);
        view.getMessageListPanel().clearMessages();
        chatReadListener.accept(partner.getUsername());

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
            // Kiểm tra trên EDT vì người dùng có thể đã đổi hội thoại trong lúc chờ
            String activeUser = view.getCurrentPartnerUsername();
            if (activeUser != null && activeUser.equalsIgnoreCase(targetUser)) {
                activeConversationId = resData.get("conversationId").getAsInt();
                view.getMessageListPanel().setMessages(list);
            }
        });
    }

    // Server xác nhận đã lưu thì mới hiện tin của mình lên khung chat
    public void onSendMessageSuccess(JsonObject msgData) {
        if (msgData == null) return;

        ChatMessage msg = ChatMessage.fromJson(msgData, currentUsername);
        String receiver = msg.getReceiver();
        SwingUtilities.invokeLater(() -> {
            String activeUser = view.getCurrentPartnerUsername();
            if (activeUser != null && activeUser.equalsIgnoreCase(receiver)) {
                activeConversationId = msg.getConversationId();
                view.getMessageListPanel().addMessage(msg);
            }
        });
    }

    // RECEIVE_PRIVATE_MESSAGE: người khác vừa gửi tin tới
    public void onMessageReceived(JsonObject msgData) {
        if (msgData == null) return;

        ChatMessage msg = ChatMessage.fromJson(msgData, currentUsername);
        String sender = msg.getSender();
        SwingUtilities.invokeLater(() -> {
            String activeUser = view.getCurrentPartnerUsername();
            boolean samePartner = activeUser != null && activeUser.equalsIgnoreCase(sender);
            if (samePartner) {
                activeConversationId = msg.getConversationId();
                // Vẫn cập nhật hội thoại ẩn để trở lại tab không bị thiếu tin
                view.getMessageListPanel().addMessage(msg);
            }

            if (samePartner && isWatchingChat()) {
                markMessagesRead(msg.getConversationId());
            } else {
                unreadListener.accept(sender);
            }
        });
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
