package private_chat.view;

import private_chat.model.ChatPartner;

import javax.swing.*;
import java.awt.*;

public class ChatHeaderPanel extends JPanel {
    private final JLabel lblFullName = new JLabel();
    private final JLabel lblStatus = new JLabel();
    private final JLabel lblAvatar = new JLabel();
    private ChatPartner currentPartner;

    public ChatHeaderPanel() {
        setLayout(new BorderLayout(12, 0));
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(225, 228, 232)),
                BorderFactory.createEmptyBorder(10, 16, 10, 16)));
        setBackground(Color.WHITE);

        // Avatar tròn mặc định hiển thị chữ cái đầu tiên
        lblAvatar.setPreferredSize(new Dimension(42, 42));
        lblAvatar.setHorizontalAlignment(SwingConstants.CENTER);
        lblAvatar.setFont(new Font("Segoe UI", Font.BOLD, 18));
        lblAvatar.setForeground(Color.WHITE);
        lblAvatar.setOpaque(true);
        lblAvatar.setBackground(new Color(0, 132, 255));

        // Thông tin người nhận
        JPanel infoPanel = new JPanel();
        infoPanel.setLayout(new BoxLayout(infoPanel, BoxLayout.Y_AXIS));
        infoPanel.setOpaque(false);

        lblFullName.setFont(new Font("Segoe UI", Font.BOLD, 15));
        lblFullName.setForeground(new Color(33, 37, 41));

        lblStatus.setFont(new Font("Segoe UI", Font.PLAIN, 12));

        infoPanel.add(lblFullName);
        infoPanel.add(Box.createVerticalStrut(2));
        infoPanel.add(lblStatus);

        add(lblAvatar, BorderLayout.WEST);
        add(infoPanel, BorderLayout.CENTER);
    }

    /**
     * Cập nhật thông tin đối tác chat khi bấm chọn từ danh sách.
     */
    public void setPartner(ChatPartner partner) {
        this.currentPartner = partner;
        if (partner == null) {
            lblFullName.setText("");
            lblStatus.setText("");
            lblAvatar.setText("");
            return;
        }

        String displayName = partner.getDisplayName();
        lblFullName.setText(displayName);

        // Hiển thị chữ cái đầu tiên lên Avatar
        String firstLetter = displayName.isEmpty() ? "?" : displayName.substring(0, 1).toUpperCase();
        lblAvatar.setText(firstLetter);

        updateStatus(partner.getStatus());
    }

    /**
     * Cập nhật trạng thái Online / Offline thời gian thực.
     */
    public void updateStatus(String status) {
        if (currentPartner != null) {
            currentPartner.setStatus(status);
        }

        if ("ONLINE".equalsIgnoreCase(status)) {
            lblStatus.setText("● Đang hoạt động");
            lblStatus.setForeground(new Color(40, 167, 69)); // Xanh lá
        } else {
            lblStatus.setText("● Không hoạt động");
            lblStatus.setForeground(new Color(130, 130, 130)); // Xám
        }
    }

    public ChatPartner getCurrentPartner() {
        return currentPartner;
    }
}
