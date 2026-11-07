package group_chat.view;

import group_chat.model.GroupItem;
import private_chat.view.ChatInputPanel;
import private_chat.view.MessageListPanel;

import javax.swing.*;
import java.awt.*;

public class GroupChatPanel extends JPanel {
    private static final String CARD_PLACEHOLDER = "PLACEHOLDER";
    private static final String CARD_CHAT = "CHAT";

    private final CardLayout cardLayout = new CardLayout();
    private final JPanel cardsPanel = new JPanel(cardLayout);

    private final JLabel lblName = new JLabel();
    private final JLabel lblInfo = new JLabel();
    private final JButton btnMembers = new JButton("Thành viên");
    private final MessageListPanel messageListPanel = new MessageListPanel();
    private final ChatInputPanel inputPanel = new ChatInputPanel();

    private GroupItem currentGroup;

    public GroupChatPanel() {
        setLayout(new BorderLayout());

        // 1. Màn hình chờ khi chưa chọn nhóm
        JPanel placeholderPanel = new JPanel(new GridBagLayout());
        placeholderPanel.setBackground(new Color(250, 250, 252));
        JLabel lblPlaceholder = new JLabel("Chọn một nhóm ở danh sách bên trái hoặc tạo nhóm mới");
        lblPlaceholder.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        lblPlaceholder.setForeground(new Color(130, 130, 130));
        placeholderPanel.add(lblPlaceholder);

        // 2. Header: tên nhóm, thông tin nhóm và nút xem thành viên
        JPanel headerPanel = new JPanel(new BorderLayout(12, 0));
        headerPanel.setBackground(Color.WHITE);
        headerPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(225, 228, 232)),
                BorderFactory.createEmptyBorder(10, 16, 10, 16)));

        lblName.setFont(new Font("Segoe UI", Font.BOLD, 15));
        lblInfo.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        lblInfo.setForeground(new Color(130, 130, 130));

        JPanel infoPanel = new JPanel();
        infoPanel.setLayout(new BoxLayout(infoPanel, BoxLayout.Y_AXIS));
        infoPanel.setOpaque(false);
        infoPanel.add(lblName);
        infoPanel.add(Box.createVerticalStrut(2));
        infoPanel.add(lblInfo);

        btnMembers.setFocusPainted(false);

        headerPanel.add(infoPanel, BorderLayout.CENTER);
        headerPanel.add(btnMembers, BorderLayout.EAST);

        // 3. Danh sách tin nhắn (hiện tên người gửi) và ô nhập (nhóm chỉ gửi chữ)
        messageListPanel.setShowSenderName(true);
        inputPanel.hideMediaButtons();

        JPanel activeChatPanel = new JPanel(new BorderLayout());
        activeChatPanel.add(headerPanel, BorderLayout.NORTH);
        activeChatPanel.add(messageListPanel, BorderLayout.CENTER);
        activeChatPanel.add(inputPanel, BorderLayout.SOUTH);

        cardsPanel.add(placeholderPanel, CARD_PLACEHOLDER);
        cardsPanel.add(activeChatPanel, CARD_CHAT);
        add(cardsPanel, BorderLayout.CENTER);

        showPlaceholder();
    }

    public void showPlaceholder() {
        currentGroup = null;
        cardLayout.show(cardsPanel, CARD_PLACEHOLDER);
    }

    /**
     * Mở khung chat của một nhóm.
     */
    public void showGroup(GroupItem group) {
        updateGroup(group);
        cardLayout.show(cardsPanel, CARD_CHAT);
        inputPanel.focusInput();
    }

    /**
     * Cập nhật tên, số thành viên, trưởng nhóm trên header (khi nhóm có thay đổi).
     */
    public void updateGroup(GroupItem group) {
        this.currentGroup = group;
        lblName.setText(group.getName());
        lblInfo.setText(group.getMemberCount() + " thành viên • Trưởng nhóm: @" + group.getLeader());
    }

    public void setMembersListener(Runnable listener) {
        btnMembers.addActionListener(e -> listener.run());
    }

    public GroupItem getCurrentGroup() {
        return currentGroup;
    }

    public int getCurrentGroupId() {
        return currentGroup != null ? currentGroup.getId() : 0;
    }

    public MessageListPanel getMessageListPanel() {
        return messageListPanel;
    }

    public ChatInputPanel getInputPanel() {
        return inputPanel;
    }
}
