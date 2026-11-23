package private_chat;

import account.UserListPanel;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import private_chat.controller.PrivateChatController;
import private_chat.model.ChatMessage;
import private_chat.model.ChatPartner;
import private_chat.view.MessageListPanel;
import private_chat.view.PrivateChatPanel;

import javax.swing.*;
import java.awt.*;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;

// Chạy riêng với -Djava.awt.headless=true, không cần server hay CSDL.
public class PrivateChatUnreadTest {
    private static final StringWriter packets = new StringWriter();
    private static UserListPanel users;
    private static RecordingChatPanel view;
    private static PrivateChatController controller;
    private static int passed;

    public static void main(String[] args) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            users = new UserListPanel("me");
            JsonArray list = new JsonArray();
            for (String name : new String[]{"alice", "bob"}) {
                JsonObject user = new JsonObject();
                user.addProperty("username", name);
                user.addProperty("fullName", "alice".equals(name)
                        ? "Người dùng có họ tên rất dài để kiểm tra nhãn chưa đọc" : name);
                user.addProperty("status", "ONLINE");
                list.add(user);
            }
            users.loadUserList(list);
            view = new RecordingChatPanel();
            controller = new PrivateChatController(view, new PrintWriter(packets, true), "me");
            controller.setUnreadListener(users::addUnread);
            controller.setChatReadListener(users::clearUnread);
            users.setUserSelectListener(user -> controller.openChatWith(
                    new ChatPartner(user.getUsername(), user.getFullName(), user.getStatus())));
        });

        receive("alice", 7);
        receive("ALICE", 7);
        onEdt(() -> {
            check("Đếm tin khi chưa chọn hội thoại, không phân biệt hoa thường", row(0).contains("(2 mới)"));
            check("Nhãn chưa đọc nhìn thấy khi sidebar hẹp và tên dài", unreadFits(0, "(2 mới)"));
            check("Không tăng số của người khác", !row(1).contains("mới"));
            check("Chưa xem thì không báo đã đọc", !output().contains("MARK_MESSAGES_READ"));
            users.selectUser("alice");
            check("Mở hội thoại xóa nhãn", !row(0).contains("mới"));
        });
        history("alice", 7);
        clearOutput();
        receive("alice", 7);
        onEdt(() -> {
            check("Đang xem thì không tăng nhãn", !row(0).contains("mới"));
            check("Đang xem thì báo đã đọc", output().contains("MARK_MESSAGES_READ"));
            check("Tin hiển thị ở hội thoại đang xem", view.messages.count == 1);
            controller.setChatVisible(false);
        });
        clearOutput();
        receive("alice", 7);
        receive("alice", 7);
        onEdt(() -> {
            check("Tab nhóm vẫn đếm tin của người đang chọn", row(0).contains("(2 mới)"));
            check("Tab nhóm không đánh dấu đã đọc", !output().contains("MARK_MESSAGES_READ"));
            check("Hội thoại ẩn vẫn nhận đủ tin", view.messages.count == 3);
            controller.setChatVisible(true);
            check("Trở lại tab người dùng xóa nhãn", !row(0).contains("mới"));
            check("Trở lại tab gửi đánh dấu đúng hội thoại", output().contains("\"conversationId\":7"));
        });
        clearOutput();
        receive("bob", 8);
        onEdt(() -> {
            check("Tin người khác tăng nhãn riêng", row(1).contains("(1 mới)"));
            check("Tin người khác không chèn vào chat đang mở", view.messages.count == 3);
            check("Tin người khác không bị đánh dấu đã đọc", !output().contains("MARK_MESSAGES_READ"));
            users.selectUser("bob");
            check("Chuyển người chat xóa đúng nhãn", !row(1).contains("mới"));
        });
        history("bob", 8);
        onEdt(() -> {
            // Xếp tin vào hàng đợi rồi đổi người trước khi tác vụ xử lý được chạy.
            controller.onMessageReceived(message("alice", 7));
            users.selectUser("alice");
        });
        onEdt(() -> check("Kiểm tra người đang xem ngay trên EDT", !row(0).contains("mới")));
        clearOutput();
        onEdt(() -> {
            controller.setChatVisible(false);
            controller.setChatVisible(true);
            check("Quay lại dùng id của tin vừa nhận", output().contains("\"conversationId\":7"));
        });
        onEdt(() -> {
            controller.onChatHistoryReceived(historyData("alice", 7));
            users.selectUser("bob");
        });
        onEdt(() -> {
            clearOutput();
            controller.setChatVisible(false);
            controller.setChatVisible(true);
            check("Lịch sử cũ không ghi đè id sau khi đổi người", !output().contains("MARK_MESSAGES_READ"));
        });
        history("bob", 8);
        java.util.concurrent.atomic.AtomicBoolean active = new java.util.concurrent.atomic.AtomicBoolean(false);
        onEdt(() -> controller.setWindowActiveSupplier(active::get));
        clearOutput();
        receive("bob", 8);
        receive("bob", 8);
        onEdt(() -> {
            check("Cửa sổ ở nền vẫn đếm dù đang chọn người gửi", row(1).contains("(2 mới)"));
            check("Cửa sổ ở nền không báo đã đọc", !output().contains("MARK_MESSAGES_READ"));
            users.updateUserStatus("bob", "OFFLINE");
            check("Đổi trạng thái online/offline không làm mất bộ đếm", row(1).contains("(2 mới)"));
            controller.setChatVisible(false);
            controller.setChatVisible(true);
            check("Đổi tab khi cửa sổ chưa active không xóa bộ đếm", row(1).contains("(2 mới)"));
            active.set(true);
            controller.onWindowActivated();
            check("Quay lại cửa sổ đang xem xóa bộ đếm", !row(1).contains("mới"));
            check("Quay lại cửa sổ đánh dấu đúng hội thoại", output().contains("\"conversationId\":8"));
            controller.setChatVisible(false);
            active.set(false);
        });
        clearOutput();
        receive("bob", 8);
        onEdt(() -> {
            active.set(true);
            controller.onWindowActivated();
            check("Active cửa sổ ở tab nhóm không xóa nhãn private", row(1).contains("(1 mới)"));
            check("Active cửa sổ ở tab nhóm không báo đã đọc private", !output().contains("MARK_MESSAGES_READ"));
            controller.setChatVisible(true);
        });
        System.out.println("PrivateChatUnreadTest: " + passed + " passed, 0 failed");
    }

    private static JsonObject message(String sender, int conversationId) {
        JsonObject msg = new JsonObject();
        msg.addProperty("sender", sender);
        msg.addProperty("receiver", "me");
        msg.addProperty("conversationId", conversationId);
        msg.addProperty("content", "Tin nhắn kiểm thử");
        return msg;
    }

    private static JsonObject historyData(String target, int id) {
        JsonObject data = new JsonObject();
        data.addProperty("targetUser", target);
        data.addProperty("conversationId", id);
        data.add("messages", new JsonArray());
        return data;
    }

    private static void history(String target, int id) throws Exception {
        controller.onChatHistoryReceived(historyData(target, id));
        onEdt(() -> {});
    }

    private static void receive(String sender, int id) throws Exception {
        controller.onMessageReceived(message(sender, id));
        onEdt(() -> {});
    }

    private static void onEdt(Runnable action) throws Exception {
        SwingUtilities.invokeAndWait(action);
    }

    private static void clearOutput() {
        packets.getBuffer().setLength(0);
    }

    private static String output() {
        return packets.toString();
    }

    @SuppressWarnings("unchecked")
    private static String row(int index) {
        JScrollPane scroll = (JScrollPane) users.getComponent(0);
        JList<UserListPanel.UserItem> list = (JList<UserListPanel.UserItem>) scroll.getViewport().getView();
        Component cell = list.getCellRenderer().getListCellRendererComponent(
                list, list.getModel().getElementAt(index), index, false, false);
        StringBuilder text = new StringBuilder();
        for (Component child : ((JPanel) cell).getComponents()) {
            if (child instanceof JLabel && child.isVisible()) {
                text.append(((JLabel) child).getText());
            }
        }
        return text.toString();
    }

    @SuppressWarnings("unchecked")
    private static boolean unreadFits(int index, String expected) {
        JScrollPane scroll = (JScrollPane) users.getComponent(0);
        JList<UserListPanel.UserItem> list = (JList<UserListPanel.UserItem>) scroll.getViewport().getView();
        JPanel cell = (JPanel) list.getCellRenderer().getListCellRendererComponent(
                list, list.getModel().getElementAt(index), index, false, false);
        // Sidebar 280px còn phải trừ viền và thanh cuộn.
        cell.setSize(250, cell.getPreferredSize().height);
        cell.doLayout();
        for (Component child : cell.getComponents()) {
            if (!(child instanceof JLabel)) continue;
            JLabel label = (JLabel) child;
            int start = label.getText().indexOf(expected);
            if (start < 0) continue;
            FontMetrics metrics = label.getFontMetrics(label.getFont());
            int textEnd = metrics.stringWidth(label.getText().substring(0, start + expected.length()));
            Insets insets = label.getInsets();
            return label.isVisible() && textEnd <= label.getWidth() - insets.left - insets.right;
        }
        return false;
    }

    private static void check(String name, boolean condition) {
        if (!condition) throw new AssertionError(name);
        passed++;
        System.out.println("[PASS] " + name);
    }

    private static class RecordingChatPanel extends PrivateChatPanel {
        final RecordingMessages messages = new RecordingMessages();

        @Override
        public MessageListPanel getMessageListPanel() {
            return messages;
        }
    }

    private static class RecordingMessages extends MessageListPanel {
        int count;

        @Override
        public void addMessage(ChatMessage msg) {
            count++;
        }

        @Override
        public void setMessages(List<ChatMessage> messages) {
            count = messages.size();
        }

        @Override
        public void clearMessages() {
            count = 0;
        }
    }
}
