package private_chat.view;

import file_notification.audio.AudioPlayer;
import file_notification.controller.FileClientManager;
import file_notification.model.MediaContent;
import file_notification.view.ImageViewerDialog;
import private_chat.model.ChatMessage;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;

public class MessageBubblePanel extends JPanel {

    // Có hiện tên người gửi phía trên tin nhắn của người khác không (chat nhóm)
    private final boolean showSender;

    public MessageBubblePanel(ChatMessage msg) {
        this(msg, false);
    }

    public MessageBubblePanel(ChatMessage msg, boolean showSender) {
        this.showSender = showSender;
        setOpaque(false);

        boolean isMine = msg.isMine();
        setLayout(new FlowLayout(isMine ? FlowLayout.RIGHT : FlowLayout.LEFT, 16, 4));

        MediaContent media = MediaContent.parse(msg.getContent());
        if (media != null && media.isMedia()) {
            // Tin nhắn tệp
            add(createMediaBubble(msg, media, isMine));
        } else {
            // Tin nhắn chữ
            add(createTextBubble(msg, isMine));
        }
    }

    // Bong bóng cho tin nhắn chữ
    private JPanel createTextBubble(ChatMessage msg, boolean isMine) {
        Color bubbleBg = isMine ? new Color(0, 132, 255) : new Color(240, 242, 245);
        Color timeColor = isMine ? new Color(220, 235, 255) : new Color(130, 130, 130);

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

        String safeContent = escapeHtml(msg.getContent()).replace("\n", "<br/>");
        String htmlText = "<html><body style='width: 300px; font-family: Segoe UI, sans-serif; font-size: 11pt; color: "
                + (isMine ? "#FFFFFF" : "#141414") + ";'>" + safeContent + "</body></html>";

        JLabel lblContent = new JLabel(htmlText);
        lblContent.setFont(new Font("Segoe UI", Font.PLAIN, 13));

        String timeStr = msg.getFormattedTime();
        JLabel lblTime = new JLabel(timeStr);
        lblTime.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        lblTime.setForeground(timeColor);
        lblTime.setAlignmentX(isMine ? Component.RIGHT_ALIGNMENT : Component.LEFT_ALIGNMENT);

        // Chat nhóm: hiện tên người gửi phía trên tin nhắn của người khác
        if (showSender && !isMine) {
            JLabel lblSender = new JLabel(msg.getSenderName());
            lblSender.setFont(new Font("Segoe UI", Font.BOLD, 11));
            lblSender.setForeground(new Color(0, 100, 200));
            bubbleBox.add(lblSender);
            bubbleBox.add(Box.createVerticalStrut(2));
        }

        bubbleBox.add(lblContent);
        if (!timeStr.isEmpty()) {
            bubbleBox.add(Box.createVerticalStrut(3));
            bubbleBox.add(lblTime);
        }

        return bubbleBox;
    }

    // Bong bóng cho tệp: ảnh, ghi âm, video hoặc tài liệu
    private JPanel createMediaBubble(ChatMessage msg, MediaContent media, boolean isMine) {
        Color bubbleBg = isMine ? new Color(235, 245, 255) : new Color(245, 246, 248);
        Color borderColor = isMine ? new Color(160, 205, 255) : new Color(215, 220, 228);

        JPanel card = new JPanel(new BorderLayout(8, 6)) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(bubbleBg);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 16, 16);
                g2.setColor(borderColor);
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 16, 16);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        card.setOpaque(false);
        card.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));

        if (media.isImage()) {
            buildImageCard(card, media);
        } else if (media.isAudio()) {
            buildAudioCard(card, media);
        } else if (media.isVideo()) {
            buildVideoCard(card, media);
        } else {
            buildFileCard(card, media);
        }

        // Giờ gửi
        String timeStr = msg.getFormattedTime();
        if (!timeStr.isEmpty()) {
            JLabel lblTime = new JLabel(timeStr);
            lblTime.setFont(new Font("Segoe UI", Font.PLAIN, 10));
            lblTime.setForeground(new Color(130, 130, 130));
            lblTime.setHorizontalAlignment(isMine ? SwingConstants.RIGHT : SwingConstants.LEFT);
            card.add(lblTime, BorderLayout.SOUTH);
        }

        return card;
    }

    // Ảnh
    private void buildImageCard(JPanel card, MediaContent media) {
        JPanel centerPanel = new JPanel();
        centerPanel.setLayout(new BoxLayout(centerPanel, BoxLayout.Y_AXIS));
        centerPanel.setOpaque(false);

        JLabel lblThumb = new JLabel("Đang tải ảnh... ⌛");
        lblThumb.setFont(new Font("Segoe UI", Font.ITALIC, 12));
        lblThumb.setPreferredSize(new Dimension(220, 140));
        lblThumb.setHorizontalAlignment(SwingConstants.CENTER);
        lblThumb.setAlignmentX(Component.CENTER_ALIGNMENT);
        lblThumb.setBorder(BorderFactory.createLineBorder(new Color(220, 225, 230), 1));
        lblThumb.setCursor(new Cursor(Cursor.HAND_CURSOR));

        centerPanel.add(lblThumb);

        // Tên tệp và nút lưu
        JPanel footer = new JPanel(new BorderLayout(5, 0));
        footer.setOpaque(false);
        JLabel lblName = new JLabel("🖼️ " + media.getFileName() + " (" + media.getFormattedFileSize() + ")");
        lblName.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        lblName.setForeground(new Color(60, 64, 67));

        JButton btnSave = new JButton("💾 Lưu");
        btnSave.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        btnSave.setMargin(new Insets(2, 6, 2, 6));
        btnSave.setFocusPainted(false);

        footer.add(lblName, BorderLayout.CENTER);
        footer.add(btnSave, BorderLayout.EAST);

        centerPanel.add(Box.createVerticalStrut(6));
        centerPanel.add(footer);

        card.add(centerPanel, BorderLayout.CENTER);

        // Ảnh được tải sau, có dữ liệu rồi mới vẽ
        FileClientManager.getInstance().getFileData(media.getFileId(), imageBytes -> {
            try {
                BufferedImage original = ImageIO.read(new ByteArrayInputStream(imageBytes));
                if (original != null) {
                    // Thu nhỏ cho vừa khung 240x160
                    int maxW = 240, maxH = 160;
                    double scale = Math.min((double) maxW / original.getWidth(), (double) maxH / original.getHeight());
                    int w = (int) (original.getWidth() * Math.min(1.0, scale));
                    int h = (int) (original.getHeight() * Math.min(1.0, scale));

                    Image scaled = original.getScaledInstance(w, h, Image.SCALE_SMOOTH);
                    lblThumb.setText("");
                    lblThumb.setPreferredSize(new Dimension(w, h));
                    lblThumb.setIcon(new ImageIcon(scaled));
                    lblThumb.revalidate();
                    lblThumb.repaint();

                    // Bấm vào ảnh để mở cửa sổ xem ảnh
                    lblThumb.addMouseListener(new MouseAdapter() {
                        @Override
                        public void mouseClicked(MouseEvent e) {
                            Frame topFrame = (Frame) SwingUtilities.getWindowAncestor(card);
                            new ImageViewerDialog(topFrame, imageBytes, media.getFileName()).setVisible(true);
                        }
                    });

                    btnSave.addActionListener(e -> {
                        FileClientManager.getInstance().saveFileToDisk(card, media.getFileName(), imageBytes);
                    });
                }
            } catch (Exception ex) {
                lblThumb.setText("Lỗi nạp ảnh");
            }
        });
    }

    // Ghi âm
    private void buildAudioCard(JPanel card, MediaContent media) {
        JPanel center = new JPanel(new BorderLayout(10, 5));
        center.setOpaque(false);
        center.setPreferredSize(new Dimension(270, 50));

        JButton btnPlay = new JButton("▶ Phát");
        btnPlay.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btnPlay.setBackground(new Color(0, 132, 255));
        btnPlay.setForeground(Color.WHITE);
        btnPlay.setFocusPainted(false);
        btnPlay.setCursor(new Cursor(Cursor.HAND_CURSOR));

        JPanel infoPanel = new JPanel();
        infoPanel.setLayout(new BoxLayout(infoPanel, BoxLayout.Y_AXIS));
        infoPanel.setOpaque(false);

        JLabel lblTitle = new JLabel("🎙️ Tin nhắn thoại (" + media.getFormattedDuration() + ")");
        lblTitle.setFont(new Font("Segoe UI", Font.BOLD, 13));

        JLabel lblSub = new JLabel("Dung lượng: " + media.getFormattedFileSize());
        lblSub.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        lblSub.setForeground(new Color(110, 110, 110));

        infoPanel.add(lblTitle);
        infoPanel.add(Box.createVerticalStrut(2));
        infoPanel.add(lblSub);

        JButton btnSave = new JButton("💾");
        btnSave.setToolTipText("Lưu file âm thanh về máy");
        btnSave.setFocusPainted(false);

        center.add(btnPlay, BorderLayout.WEST);
        center.add(infoPanel, BorderLayout.CENTER);
        center.add(btnSave, BorderLayout.EAST);

        card.add(center, BorderLayout.CENTER);

        // Nút phát/dừng
        btnPlay.addActionListener(e -> {
            if (AudioPlayer.getInstance().isPlaying()) {
                AudioPlayer.getInstance().stop();
                btnPlay.setText("▶ Phát");
            } else {
                btnPlay.setText("⌛...");
                FileClientManager.getInstance().getFileData(media.getFileId(), wavBytes -> {
                    btnPlay.setText("⏸ Dừng");
                    AudioPlayer.getInstance().play(wavBytes, () -> {
                        SwingUtilities.invokeLater(() -> btnPlay.setText("▶ Phát"));
                    });
                });
            }
        });

        btnSave.addActionListener(e -> {
            FileClientManager.getInstance().getFileData(media.getFileId(), wavBytes -> {
                FileClientManager.getInstance().saveFileToDisk(card, media.getFileName(), wavBytes);
            });
        });
    }

    // Video
    private void buildVideoCard(JPanel card, MediaContent media) {
        JPanel center = new JPanel(new BorderLayout(10, 6));
        center.setOpaque(false);
        center.setPreferredSize(new Dimension(280, 55));

        JLabel lblIcon = new JLabel("🎬");
        lblIcon.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 32));

        JPanel info = new JPanel();
        info.setLayout(new BoxLayout(info, BoxLayout.Y_AXIS));
        info.setOpaque(false);

        String shortName = media.getFileName();
        if (shortName.length() > 22) {
            shortName = shortName.substring(0, 19) + "...";
        }
        JLabel lblTitle = new JLabel(shortName);
        lblTitle.setFont(new Font("Segoe UI", Font.BOLD, 13));

        JLabel lblSub = new JLabel("Video • " + media.getFormattedFileSize());
        lblSub.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        lblSub.setForeground(new Color(110, 110, 110));

        info.add(lblTitle);
        info.add(Box.createVerticalStrut(2));
        info.add(lblSub);

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        btnPanel.setOpaque(false);

        JButton btnOpen = new JButton("▶ Xem");
        btnOpen.setFont(new Font("Segoe UI", Font.BOLD, 11));
        btnOpen.setBackground(new Color(0, 132, 255));
        btnOpen.setForeground(Color.WHITE);
        btnOpen.setFocusPainted(false);
        btnOpen.setCursor(new Cursor(Cursor.HAND_CURSOR));

        JButton btnSave = new JButton("💾");
        btnSave.setToolTipText("Lưu video về máy");
        btnSave.setFocusPainted(false);

        btnPanel.add(btnOpen);
        btnPanel.add(btnSave);

        center.add(lblIcon, BorderLayout.WEST);
        center.add(info, BorderLayout.CENTER);
        center.add(btnPanel, BorderLayout.EAST);

        card.add(center, BorderLayout.CENTER);

        // Mở bằng trình phát của hệ điều hành
        btnOpen.addActionListener(e -> {
            btnOpen.setText("⌛...");
            FileClientManager.getInstance().getFileData(media.getFileId(), videoBytes -> {
                btnOpen.setText("▶ Xem");
                FileClientManager.getInstance().openVideo(card, media.getFileName(), videoBytes);
            });
        });

        btnSave.addActionListener(e -> {
            FileClientManager.getInstance().getFileData(media.getFileId(), videoBytes -> {
                FileClientManager.getInstance().saveFileToDisk(card, media.getFileName(), videoBytes);
            });
        });
    }

    // Tài liệu và các tệp khác
    private void buildFileCard(JPanel card, MediaContent media) {
        JPanel center = new JPanel(new BorderLayout(10, 6));
        center.setOpaque(false);
        center.setPreferredSize(new Dimension(280, 50));

        JLabel lblIcon = new JLabel("📄");
        lblIcon.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 28));

        JPanel info = new JPanel();
        info.setLayout(new BoxLayout(info, BoxLayout.Y_AXIS));
        info.setOpaque(false);

        String shortName = media.getFileName();
        if (shortName.length() > 22) {
            shortName = shortName.substring(0, 19) + "...";
        }
        JLabel lblTitle = new JLabel(shortName);
        lblTitle.setFont(new Font("Segoe UI", Font.BOLD, 13));

        JLabel lblSub = new JLabel("Tệp • " + media.getFormattedFileSize());
        lblSub.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        lblSub.setForeground(new Color(110, 110, 110));

        info.add(lblTitle);
        info.add(Box.createVerticalStrut(2));
        info.add(lblSub);

        JButton btnDownload = new JButton("💾 Tải");
        btnDownload.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        btnDownload.setFocusPainted(false);
        btnDownload.setCursor(new Cursor(Cursor.HAND_CURSOR));

        center.add(lblIcon, BorderLayout.WEST);
        center.add(info, BorderLayout.CENTER);
        center.add(btnDownload, BorderLayout.EAST);

        card.add(center, BorderLayout.CENTER);

        btnDownload.addActionListener(e -> {
            btnDownload.setText("⌛...");
            FileClientManager.getInstance().getFileData(media.getFileId(), fileBytes -> {
                btnDownload.setText("💾 Tải");
                FileClientManager.getInstance().saveFileToDisk(card, media.getFileName(), fileBytes);
            });
        });
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
