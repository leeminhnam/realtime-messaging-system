package private_chat.view;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;

public class ChatInputPanel extends JPanel {

    @FunctionalInterface
    public interface SendMessageListener {
        void onSendMessage(String content);
    }

    private final JTextField txtInput = new JTextField();
    private final JButton btnSend = new JButton("Gửi");
    private SendMessageListener sendListener;

    public ChatInputPanel() {
        setLayout(new BorderLayout(10, 0));
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(225, 228, 232)),
                BorderFactory.createEmptyBorder(12, 16, 12, 16)));
        setBackground(Color.WHITE);

        // Ô nhập tin nhắn
        txtInput.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        txtInput.putClientProperty("JTextField.placeholderText", "Nhập tin nhắn... (nhấn Enter để gửi)");
        txtInput.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(205, 210, 218), 1, true),
                BorderFactory.createEmptyBorder(8, 12, 8, 12)));

        // Nút Gửi
        btnSend.setFont(new Font("Segoe UI", Font.BOLD, 13));
        btnSend.setBackground(new Color(0, 132, 255));
        btnSend.setForeground(Color.WHITE);
        btnSend.setFocusPainted(false);
        btnSend.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnSend.setPreferredSize(new Dimension(80, 38));

        // Bắt sự kiện Click nút Gửi
        btnSend.addActionListener(e -> triggerSend());

        // Bắt sự kiện gõ phím Enter trên ô nhập
        txtInput.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    triggerSend();
                }
            }
        });

        add(txtInput, BorderLayout.CENTER);
        add(btnSend, BorderLayout.EAST);
    }

    private void triggerSend() {
        String text = txtInput.getText().trim();
        if (!text.isEmpty()) {
            if (sendListener != null) {
                sendListener.onSendMessage(text);
            }
            txtInput.setText("");
            txtInput.requestFocusInWindow();
        }
    }

    public void setSendMessageListener(SendMessageListener listener) {
        this.sendListener = listener;
    }

    public void setInputEnabled(boolean enabled) {
        txtInput.setEnabled(enabled);
        btnSend.setEnabled(enabled);
    }

    public void focusInput() {
        txtInput.requestFocusInWindow();
    }
}
