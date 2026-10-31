package file_notification.audio;

import javax.swing.*;
import java.awt.*;

public class VoiceRecordDialog extends JDialog {

    @FunctionalInterface
    public interface VoiceRecordCallback {
        void onSendVoice(byte[] wavBytes, int durationSeconds);
    }

    private final AudioRecorder recorder = new AudioRecorder();
    private final VoiceRecordCallback callback;

    private JLabel lblTimer;
    private JLabel lblStatus;
    private JButton btnStopAndSend;
    private JButton btnPreview;
    private JButton btnCancel;
    private Timer timer;
    private int elapsedSeconds = 0;
    private byte[] recordedBytes = null;

    public VoiceRecordDialog(Frame parent, VoiceRecordCallback callback) {
        super(parent, "Ghi âm tin nhắn thoại", true);
        this.callback = callback;

        setSize(380, 220);
        setLocationRelativeTo(parent);
        setResizable(false);

        initUI();
        startRecording();
    }

    private void initUI() {
        JPanel contentPane = new JPanel(new BorderLayout(15, 15));
        contentPane.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        contentPane.setBackground(Color.WHITE);

        // Center Panel: Icon mic, Trạng thái, Bộ đếm thời gian
        JPanel centerPanel = new JPanel();
        centerPanel.setLayout(new BoxLayout(centerPanel, BoxLayout.Y_AXIS));
        centerPanel.setBackground(Color.WHITE);

        lblStatus = new JLabel("🔴 Đang ghi âm...");
        lblStatus.setFont(new Font("Segoe UI", Font.BOLD, 15));
        lblStatus.setForeground(new Color(220, 53, 69)); // Đỏ nổi bật
        lblStatus.setAlignmentX(Component.CENTER_ALIGNMENT);

        lblTimer = new JLabel("00:00");
        lblTimer.setFont(new Font("Segoe UI", Font.BOLD, 32));
        lblTimer.setForeground(new Color(33, 37, 41));
        lblTimer.setAlignmentX(Component.CENTER_ALIGNMENT);

        centerPanel.add(Box.createVerticalGlue());
        centerPanel.add(lblStatus);
        centerPanel.add(Box.createVerticalStrut(10));
        centerPanel.add(lblTimer);
        centerPanel.add(Box.createVerticalGlue());

        // Bottom Panel: Các nút điều khiển
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
        bottomPanel.setBackground(Color.WHITE);

        btnStopAndSend = new JButton("⏹ Dừng & Gửi");
        btnStopAndSend.setFont(new Font("Segoe UI", Font.BOLD, 13));
        btnStopAndSend.setBackground(new Color(0, 132, 255));
        btnStopAndSend.setForeground(Color.WHITE);
        btnStopAndSend.setFocusPainted(false);
        btnStopAndSend.setCursor(new Cursor(Cursor.HAND_CURSOR));

        btnPreview = new JButton("▶ Nghe lại");
        btnPreview.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        btnPreview.setEnabled(false);
        btnPreview.setFocusPainted(false);

        btnCancel = new JButton("Hủy bỏ");
        btnCancel.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        btnCancel.setFocusPainted(false);

        bottomPanel.add(btnStopAndSend);
        bottomPanel.add(btnPreview);
        bottomPanel.add(btnCancel);

        contentPane.add(centerPanel, BorderLayout.CENTER);
        contentPane.add(bottomPanel, BorderLayout.SOUTH);

        setContentPane(contentPane);

        // Xử lý sự kiện các nút
        btnStopAndSend.addActionListener(e -> {
            if (recorder.isRecording()) {
                stopRecording();
            }
            if (recordedBytes != null && recordedBytes.length > 0) {
                if (callback != null) {
                    callback.onSendVoice(recordedBytes, Math.max(1, elapsedSeconds));
                }
                dispose();
            }
        });

        btnPreview.addActionListener(e -> {
            if (recordedBytes != null) {
                if (AudioPlayer.getInstance().isPlaying()) {
                    AudioPlayer.getInstance().stop();
                    btnPreview.setText("▶ Nghe lại");
                } else {
                    btnPreview.setText("⏸ Tạm dừng");
                    AudioPlayer.getInstance().play(recordedBytes, () -> {
                        SwingUtilities.invokeLater(() -> btnPreview.setText("▶ Nghe lại"));
                    });
                }
            }
        });

        btnCancel.addActionListener(e -> {
            cancelRecording();
            dispose();
        });
    }

    private void startRecording() {
        try {
            recorder.startRecording();
            elapsedSeconds = 0;
            timer = new Timer(1000, e -> {
                elapsedSeconds++;
                int m = elapsedSeconds / 60;
                int s = elapsedSeconds % 60;
                lblTimer.setText(String.format("%02d:%02d", m, s));
            });
            timer.start();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Không thể kích hoạt Microphone: " + ex.getMessage(),
                    "Lỗi thiết bị âm thanh", JOptionPane.ERROR_MESSAGE);
            dispose();
        }
    }

    private void stopRecording() {
        if (timer != null) timer.stop();
        recordedBytes = recorder.stopRecording();
        lblStatus.setText("Đã hoàn tất ghi âm");
        lblStatus.setForeground(new Color(40, 167, 69)); // Xanh lá
        btnStopAndSend.setText("Gửi đoạn ghi âm");
        btnPreview.setEnabled(true);
    }

    private void cancelRecording() {
        if (timer != null) timer.stop();
        recorder.cancelRecording();
        AudioPlayer.getInstance().stop();
    }
}
