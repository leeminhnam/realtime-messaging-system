package private_chat.view;

import file_notification.audio.VoiceRecordDialog;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.File;

public class ChatInputPanel extends JPanel {

    @FunctionalInterface
    public interface SendMessageListener {
        void onSendMessage(String content);
    }

    @FunctionalInterface
    public interface SendFileListener {
        void onSendFile(File file, String fileType);
    }

    @FunctionalInterface
    public interface SendVoiceListener {
        void onSendVoice(byte[] wavBytes, int durationSeconds);
    }

    private final JTextField txtInput = new JTextField();
    private final JButton btnSend = new JButton("Gửi");
    private final JButton btnAttach = new JButton("📎");
    private final JButton btnVoice = new JButton("🎙️");

    private SendMessageListener sendListener;
    private SendFileListener sendFileListener;
    private SendVoiceListener sendVoiceListener;

    public ChatInputPanel() {
        setLayout(new BorderLayout(8, 0));
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(225, 228, 232)),
                BorderFactory.createEmptyBorder(10, 14, 10, 14)));
        setBackground(Color.WHITE);

        // Nút đính kèm và nút ghi âm
        JPanel leftActionPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        leftActionPanel.setOpaque(false);

        btnAttach.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 18));
        btnAttach.setToolTipText("Đính kèm tệp, ảnh hoặc video");
        btnAttach.setFocusPainted(false);
        btnAttach.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnAttach.setPreferredSize(new Dimension(40, 38));

        btnVoice.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 18));
        btnVoice.setToolTipText("Ghi âm tin nhắn thoại");
        btnVoice.setFocusPainted(false);
        btnVoice.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnVoice.setPreferredSize(new Dimension(40, 38));

        leftActionPanel.add(btnAttach);
        leftActionPanel.add(btnVoice);

        // Ô nhập tin nhắn
        txtInput.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        txtInput.putClientProperty("JTextField.placeholderText", "Nhập tin nhắn... (nhấn Enter để gửi)");
        txtInput.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(205, 210, 218), 1, true),
                BorderFactory.createEmptyBorder(8, 12, 8, 12)));

        // Nút gửi
        btnSend.setFont(new Font("Segoe UI", Font.BOLD, 13));
        btnSend.setBackground(new Color(0, 132, 255));
        btnSend.setForeground(Color.WHITE);
        btnSend.setFocusPainted(false);
        btnSend.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnSend.setPreferredSize(new Dimension(75, 38));

        // Bấm nút gửi
        btnSend.addActionListener(e -> triggerSend());

        // Nhấn Enter trong ô nhập cũng gửi
        txtInput.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    triggerSend();
                }
            }
        });

        // Nút đính kèm mở menu chọn loại tệp
        btnAttach.addActionListener(e -> showAttachMenu(btnAttach));

        // Nút ghi âm
        btnVoice.addActionListener(e -> openVoiceRecordDialog());

        add(leftActionPanel, BorderLayout.WEST);
        add(txtInput, BorderLayout.CENTER);
        add(btnSend, BorderLayout.EAST);
    }

    private void showAttachMenu(Component invoker) {
        JPopupMenu menu = new JPopupMenu();

        JMenuItem itemImage = new JMenuItem("🖼️  Gửi hình ảnh...");
        JMenuItem itemVideo = new JMenuItem("🎬  Gửi video clip...");
        JMenuItem itemFile = new JMenuItem("📄  Gửi tệp tin khác...");

        itemImage.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        itemVideo.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        itemFile.setFont(new Font("Segoe UI", Font.PLAIN, 13));

        itemImage.addActionListener(e -> chooseAndSendFile("IMAGE", "Hình ảnh (*.png, *.jpg, *.jpeg, *.gif)", "png", "jpg", "jpeg", "gif", "bmp"));
        itemVideo.addActionListener(e -> chooseAndSendFile("VIDEO", "Video (*.mp4, *.avi, *.mkv, *.mov)", "mp4", "avi", "mkv", "mov", "wmv"));
        itemFile.addActionListener(e -> chooseAndSendFile("FILE", "Tất cả các tệp (*.*)"));

        menu.add(itemImage);
        menu.add(itemVideo);
        menu.addSeparator();
        menu.add(itemFile);

        menu.show(invoker, 0, -menu.getPreferredSize().height);
    }

    private void chooseAndSendFile(String fileType, String description, String... extensions) {
        JFileChooser chooser = new JFileChooser();
        if (extensions != null && extensions.length > 0) {
            chooser.setFileFilter(new FileNameExtensionFilter(description, extensions));
        }

        int result = chooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            File selectedFile = chooser.getSelectedFile();
            if (sendFileListener != null) {
                sendFileListener.onSendFile(selectedFile, fileType);
            }
        }
    }

    private void openVoiceRecordDialog() {
        Frame parentFrame = (Frame) SwingUtilities.getWindowAncestor(this);
        VoiceRecordDialog dialog = new VoiceRecordDialog(parentFrame, (wavBytes, durationSeconds) -> {
            if (sendVoiceListener != null) {
                sendVoiceListener.onSendVoice(wavBytes, durationSeconds);
            }
        });
        dialog.setVisible(true);
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

    public void setSendFileListener(SendFileListener listener) {
        this.sendFileListener = listener;
    }

    public void setSendVoiceListener(SendVoiceListener listener) {
        this.sendVoiceListener = listener;
    }

    public void setInputEnabled(boolean enabled) {
        txtInput.setEnabled(enabled);
        btnSend.setEnabled(enabled);
        btnAttach.setEnabled(enabled);
        btnVoice.setEnabled(enabled);
    }

    // Chat nhóm chỉ gửi tin chữ nên ẩn hai nút này
    public void hideMediaButtons() {
        btnAttach.setVisible(false);
        btnVoice.setVisible(false);
    }

    public void focusInput() {
        txtInput.requestFocusInWindow();
    }
}
