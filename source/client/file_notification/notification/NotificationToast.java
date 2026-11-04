package file_notification.notification;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;
import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class NotificationToast extends JWindow {

    @FunctionalInterface
    public interface NotificationClickListener {
        void onClick(String senderUsername);
    }

    private static NotificationToast activeToast = null;

    public static synchronized void showNotification(JFrame parent, String type, String senderUsername,
                                                      String senderFullName, String message,
                                                      NotificationClickListener listener) {
        // Đóng toast cũ nếu đang mở
        if (activeToast != null) {
            activeToast.dispose();
            activeToast = null;
        }

        activeToast = new NotificationToast(parent, type, senderUsername, senderFullName, message, listener);
        activeToast.setVisible(true);

        // Phát âm thanh thông báo dịu nhẹ
        playChimeSound();
    }

    private NotificationToast(JFrame parent, String type, String senderUsername, String senderFullName,
                              String message, NotificationClickListener listener) {
        super(parent);

        setSize(320, 85);

        // Đặt vị trí góc dưới bên phải màn hình
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        Rectangle screenRect = ge.getMaximumWindowBounds();
        int x = screenRect.x + screenRect.width - getWidth() - 20;
        int y = screenRect.y + screenRect.height - getHeight() - 20;
        setLocation(x, y);

        setAlwaysOnTop(true);

        JPanel panel = new JPanel(new BorderLayout(10, 5)) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(255, 255, 255));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 16, 16);
                g2.setColor(new Color(0, 132, 255));
                g2.setStroke(new BasicStroke(2));
                g2.drawRoundRect(1, 1, getWidth() - 2, getHeight() - 2, 16, 16);
                g2.dispose();
            }
        };
        panel.setBorder(BorderFactory.createEmptyBorder(10, 14, 10, 14));
        panel.setCursor(new Cursor(Cursor.HAND_CURSOR));

        // Icon đại diện loại thông báo
        String iconText;
        switch (type != null ? type.toUpperCase() : "TEXT") {
            case "IMAGE":
                iconText = "🖼️";
                break;
            case "AUDIO":
                iconText = "🎙️";
                break;
            case "VIDEO":
                iconText = "🎬";
                break;
            case "FILE":
                iconText = "📄";
                break;
            default:
                iconText = "💬";
                break;
        }

        JLabel lblIcon = new JLabel(iconText);
        lblIcon.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 28));

        // Nội dung thông báo
        JPanel textPanel = new JPanel();
        textPanel.setLayout(new BoxLayout(textPanel, BoxLayout.Y_AXIS));
        textPanel.setOpaque(false);

        JLabel lblSender = new JLabel(senderFullName + " (@" + senderUsername + ")");
        lblSender.setFont(new Font("Segoe UI", Font.BOLD, 13));
        lblSender.setForeground(new Color(20, 20, 20));

        String safeMsg = message != null ? message : "Đã gửi một thông điệp";
        if (safeMsg.length() > 50) {
            safeMsg = safeMsg.substring(0, 48) + "...";
        }
        JLabel lblMessage = new JLabel(safeMsg);
        lblMessage.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        lblMessage.setForeground(new Color(90, 90, 90));

        textPanel.add(lblSender);
        textPanel.add(Box.createVerticalStrut(3));
        textPanel.add(lblMessage);

        panel.add(lblIcon, BorderLayout.WEST);
        panel.add(textPanel, BorderLayout.CENTER);

        // Nút tắt nhỏ
        JLabel lblClose = new JLabel("✕");
        lblClose.setFont(new Font("Segoe UI", Font.BOLD, 12));
        lblClose.setForeground(new Color(160, 160, 160));
        lblClose.setCursor(new Cursor(Cursor.HAND_CURSOR));
        lblClose.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                dispose();
            }
        });
        panel.add(lblClose, BorderLayout.EAST);

        setContentPane(panel);

        // Nhấn vào thông báo để chuyển ngay tới cuộc trò chuyện
        panel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (listener != null) {
                    listener.onClick(senderUsername);
                }
                dispose();
            }
        });

        // Tự động đóng sau 4.5 giây
        Timer autoCloseTimer = new Timer(4500, e -> dispose());
        autoCloseTimer.setRepeats(false);
        autoCloseTimer.start();
    }

    /**
     * Tiện ích phát âm thanh thông báo dạng chuông tinh tế sử dụng Java Sound.
     */
    private static void playChimeSound() {
        new Thread(() -> {
            try {
                AudioFormat format = new AudioFormat(44100, 8, 1, true, false);
                SourceDataLine line = AudioSystem.getSourceDataLine(format);
                line.open(format);
                line.start();

                // Tạo 2 nốt nhạc ngắn tạo âm thanh "Ding-Dong" êm ái
                byte[] ding = generateTone(880, 70, 44100);
                byte[] dong = generateTone(1174, 110, 44100);

                line.write(ding, 0, ding.length);
                line.write(dong, 0, dong.length);
                line.drain();
                line.stop();
                line.close();
            } catch (Exception e) {
                // Fallback nếu máy tính không hỗ trợ SourceDataLine
                Toolkit.getDefaultToolkit().beep();
            }
        }).start();
    }

    private static byte[] generateTone(int freq, int ms, int sampleRate) {
        int length = (int) (sampleRate * (ms / 1000.0));
        byte[] sin = new byte[length];
        for (int i = 0; i < length; i++) {
            double angle = 2.0 * Math.PI * i / (sampleRate / (double) freq);
            // Giảm dần âm lượng để không bị chói tai (fade out)
            double decay = 1.0 - ((double) i / length);
            sin[i] = (byte) (Math.sin(angle) * 70 * decay);
        }
        return sin;
    }
}
