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

        // Bọc trong JScrollPane
        scrollPane = new JScrollPane(contentPanel);
        scrollPane.setBorder(null);
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);

        add(scrollPane, BorderLayout.CENTER);
    }

    // Ngày của tin nhắn cuối cùng đang hiển thị, để biết khi nào cần vạch ngăn ngày mới
    private String lastDateKey = null;

    // Thêm tin vào cuối rồi cuộn xuống
    public void addMessage(ChatMessage msg) {
        appendMessage(msg);
        contentPanel.revalidate();
        contentPanel.repaint();
        scrollToBottom();
    }

    // Nạp toàn bộ lịch sử tin nhắn
    public void setMessages(List<ChatMessage> messages) {
        contentPanel.removeAll();
        lastDateKey = null;
        if (messages != null) {
            for (ChatMessage msg : messages) {
                appendMessage(msg);
            }
        }
        contentPanel.revalidate();
        contentPanel.repaint();
        scrollToBottom();
    }

    // Xóa hết bong bóng tin nhắn
    public void clearMessages() {
        contentPanel.removeAll();
        lastDateKey = null;
        contentPanel.revalidate();
        contentPanel.repaint();
    }

    // Sang ngày khác so với tin trước thì chèn vạch ngăn ghi ngày, rồi mới thêm bong bóng
    private void appendMessage(ChatMessage msg) {
        String dateKey = msg.getDateKey();
        if (!dateKey.isEmpty() && !dateKey.equals(lastDateKey)) {
            contentPanel.add(createDateSeparator(msg.getFormattedDate()));
            lastDateKey = dateKey;
        }
        contentPanel.add(new MessageBubblePanel(msg, showSenderName));
    }

    // Nhãn ngày nằm giữa khung chat
    private JPanel createDateSeparator(String text) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 8));
        row.setOpaque(false);

        JLabel lbl = new JLabel(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(228, 230, 235));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), getHeight(), getHeight());
                g2.dispose();
                super.paintComponent(g);
            }
        };
        lbl.setFont(new Font("Segoe UI", Font.BOLD, 11));
        lbl.setForeground(new Color(101, 103, 107));
        lbl.setBorder(BorderFactory.createEmptyBorder(3, 12, 3, 12));
        row.add(lbl);

        // Không để BoxLayout kéo giãn hàng này theo chiều cao
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
        return row;
    }

    // Cuộn sau khi layout xong, nếu không sẽ chưa xuống hết
    public void scrollToBottom() {
        SwingUtilities.invokeLater(() -> {
            JScrollBar vertical = scrollPane.getVerticalScrollBar();
            vertical.setValue(vertical.getMaximum());
        });
    }
}
