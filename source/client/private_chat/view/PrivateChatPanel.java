package private_chat.view;

import private_chat.model.ChatPartner;

import javax.swing.*;
import java.awt.*;

public class PrivateChatPanel extends JPanel {
    private final CardLayout cardLayout = new CardLayout();
    private final JPanel cardsPanel = new JPanel(cardLayout);

    private final ChatHeaderPanel headerPanel = new ChatHeaderPanel();
    private final MessageListPanel messageListPanel = new MessageListPanel();
    private final ChatInputPanel inputPanel = new ChatInputPanel();

    private static final String CARD_PLACEHOLDER = "PLACEHOLDER";
    private static final String CARD_CHAT = "CHAT";

    public PrivateChatPanel() {
        setLayout(new BorderLayout());

        // Màn hình chờ khi chưa chọn ai để chat
        JPanel placeholderPanel = new JPanel(new GridBagLayout());
        placeholderPanel.setBackground(new Color(250, 250, 252));
        JLabel lblPlaceholder = new JLabel("Chọn một người dùng ở danh sách bên trái để bắt đầu trò chuyện");
        lblPlaceholder.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        lblPlaceholder.setForeground(new Color(130, 130, 130));
        placeholderPanel.add(lblPlaceholder);

        // Khung chat: header, danh sách tin và ô nhập
        JPanel activeChatPanel = new JPanel(new BorderLayout());
        activeChatPanel.add(headerPanel, BorderLayout.NORTH);
        activeChatPanel.add(messageListPanel, BorderLayout.CENTER);
        activeChatPanel.add(inputPanel, BorderLayout.SOUTH);

        // Đưa vào CardLayout
        cardsPanel.add(placeholderPanel, CARD_PLACEHOLDER);
        cardsPanel.add(activeChatPanel, CARD_CHAT);

        add(cardsPanel, BorderLayout.CENTER);

        // Ban đầu hiện màn hình chờ
        showPlaceholder();
    }

    public void showPlaceholder() {
        cardLayout.show(cardsPanel, CARD_PLACEHOLDER);
    }

    public void showChat(ChatPartner partner) {
        headerPanel.setPartner(partner);
        cardLayout.show(cardsPanel, CARD_CHAT);
        inputPanel.focusInput();
    }

    public ChatHeaderPanel getHeaderPanel() {
        return headerPanel;
    }

    public MessageListPanel getMessageListPanel() {
        return messageListPanel;
    }

    public ChatInputPanel getInputPanel() {
        return inputPanel;
    }

    public String getCurrentPartnerUsername() {
        ChatPartner partner = headerPanel.getCurrentPartner();
        return partner != null ? partner.getUsername() : null;
    }
}
