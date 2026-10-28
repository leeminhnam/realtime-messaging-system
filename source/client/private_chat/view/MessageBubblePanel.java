package private_chat.view;

import private_chat.model.ChatMessage;

import javax.swing.*;
import java.awt.*;

public class MessageBubblePanel extends JPanel {

    public MessageBubblePanel(ChatMessage msg) {
        setOpaque(false);

        boolean isMine = msg.isMine();
        setLayout(new FlowLayout(isMine ? FlowLayout.RIGHT : FlowLayout.LEFT, 16, 3));

        // Màu sắc theo người gửi
        Color bubbleBg = isMine ? new Color(0, 132, 255) : new Color(240, 242, 245);
        Color textColor = isMine ? Color.WHITE : new Color(20, 20, 20);
        Color timeColor = isMine ? new Color(220, 235, 255) : new Color(130, 130, 130);

        // Khung bong bóng bo góc
        JPanel bubbleBox = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(bubbleBg);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 16, 16);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        bubbleBox.setOpaque(false);
        bubbleBox.setLayout(new BoxLayout(bubbleBox, BoxLayout.Y_AXIS));
        bubbleBox.setBorder(BorderFactory.createEmptyBorder(8, 12, 6, 12));

        // Nội dung tin nhắn (Dùng HTML để tự động xuống dòng và hỗ trợ unicode)
        String safeContent = escapeHtml(msg.getContent()).replace("\n", "<br/>");
        String htmlText = "<html><body style='width: 300px; font-family: Segoe UI, sans-serif; font-size: 11pt; color: "
                + (isMine ? "#FFFFFF" : "#141414") + ";'>" + safeContent + "</body></html>";

        JLabel lblContent = new JLabel(htmlText);
        lblContent.setFont(new Font("Segoe UI", Font.PLAIN, 13));

        // Nhãn thời gian gửi ở chân tin nhắn
        String timeStr = msg.getFormattedTime();
        JLabel lblTime = new JLabel(timeStr);
        lblTime.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        lblTime.setForeground(timeColor);
        lblTime.setAlignmentX(isMine ? Component.RIGHT_ALIGNMENT : Component.LEFT_ALIGNMENT);

        bubbleBox.add(lblContent);
        if (!timeStr.isEmpty()) {
            bubbleBox.add(Box.createVerticalStrut(3));
            bubbleBox.add(lblTime);
        }

        add(bubbleBox);
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
