package private_chat.view;

import private_chat.model.ChatMessage;

import javax.swing.*;
import java.awt.*;
import java.util.List;

public class MessageListPanel extends JPanel {
    private final JPanel contentPanel = new JPanel();
    private final JScrollPane scrollPane;

    // Có hiện tên người gửi trên bong bóng của người khác không (dùng cho chat nhóm)
    private boolean showSenderName = false;

    public void setShowSenderName(boolean showSenderName) {
        this.showSenderName = showSenderName;
    }

    public MessageListPanel() {
        setLayout(new BorderLayout());

        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
        contentPanel.setBackground(new Color(250, 250, 252));
        contentPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Bao quanh bằng JScrollPane
        scrollPane = new JScrollPane(contentPanel);
        scrollPane.setBorder(null);
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);

        add(scrollPane, BorderLayout.CENTER);
    }

    /**
     * Thêm một tin nhắn mới vào cuối cuộc trò chuyện và tự động cuộn xuống dưới cùng.
     */
    public void addMessage(ChatMessage msg) {
        contentPanel.add(new MessageBubblePanel(msg, showSenderName));
        contentPanel.revalidate();
        contentPanel.repaint();
        scrollToBottom();
    }

    /**
     * Tải toàn bộ danh sách lịch sử tin nhắn.
     */
    public void setMessages(List<ChatMessage> messages) {
        contentPanel.removeAll();
        if (messages != null) {
            for (ChatMessage msg : messages) {
                contentPanel.add(new MessageBubblePanel(msg, showSenderName));
            }
        }
        contentPanel.revalidate();
        contentPanel.repaint();
        scrollToBottom();
    }

    /**
     * Xóa sạch các bong bóng tin nhắn.
     */
    public void clearMessages() {
        contentPanel.removeAll();
        contentPanel.revalidate();
        contentPanel.repaint();
    }

    /**
     * Tự động cuộn thanh cuộn xuống tin nhắn mới nhất ở đáy.
     */
    public void scrollToBottom() {
        SwingUtilities.invokeLater(() -> {
            JScrollBar vertical = scrollPane.getVerticalScrollBar();
            vertical.setValue(vertical.getMaximum());
        });
    }
}
