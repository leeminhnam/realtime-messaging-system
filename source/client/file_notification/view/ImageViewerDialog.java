package file_notification.view;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;

public class ImageViewerDialog extends JDialog {
    private final byte[] imageBytes;
    private final String fileName;
    private BufferedImage originalImage;
    private double zoomFactor = 1.0;
    private JLabel lblImage;
    private JScrollPane scrollPane;

    public ImageViewerDialog(Frame parent, byte[] imageBytes, String fileName) {
        super(parent, "Xem ảnh: " + fileName, true);
        this.imageBytes = imageBytes;
        this.fileName = fileName;

        setSize(800, 600);
        setLocationRelativeTo(parent);

        loadImage();
        initUI();
    }

    private void loadImage() {
        try {
            originalImage = ImageIO.read(new ByteArrayInputStream(imageBytes));
        } catch (Exception e) {
            System.err.println("[IMAGE VIEWER] Lỗi đọc ảnh: " + e.getMessage());
        }
    }

    private void initUI() {
        JPanel mainPanel = new JPanel(new BorderLayout());

        // Thanh công cụ phía trên
        JToolBar toolBar = new JToolBar();
        toolBar.setFloatable(false);
        toolBar.setBackground(new Color(245, 246, 248));

        JButton btnZoomIn = new JButton("🔍 Phóng to (+)");
        JButton btnZoomOut = new JButton("🔍 Thu nhỏ (-)");
        JButton btnReset = new JButton("Kích thước chuẩn");
        JButton btnSave = new JButton("💾 Lưu ảnh về máy...");

        btnZoomIn.addActionListener(e -> {
            zoomFactor *= 1.25;
            updateImageDisplay();
        });

        btnZoomOut.addActionListener(e -> {
            zoomFactor = Math.max(0.2, zoomFactor / 1.25);
            updateImageDisplay();
        });

        btnReset.addActionListener(e -> {
            zoomFactor = 1.0;
            updateImageDisplay();
        });

        btnSave.addActionListener(e -> saveImageToDisk());

        toolBar.add(btnZoomIn);
        toolBar.add(btnZoomOut);
        toolBar.add(btnReset);
        toolBar.add(Box.createHorizontalGlue());
        toolBar.add(btnSave);

        mainPanel.add(toolBar, BorderLayout.NORTH);

        // Khung hiển thị ảnh
        lblImage = new JLabel();
        lblImage.setHorizontalAlignment(SwingConstants.CENTER);
        lblImage.setVerticalAlignment(SwingConstants.CENTER);

        scrollPane = new JScrollPane(lblImage);
        scrollPane.getViewport().setBackground(new Color(40, 44, 52)); // Nền xám đen xem ảnh chuẩn Studio
        mainPanel.add(scrollPane, BorderLayout.CENTER);

        updateImageDisplay();
        setContentPane(mainPanel);
    }

    private void updateImageDisplay() {
        if (originalImage == null) {
            lblImage.setText("Không thể hiển thị định dạng ảnh này.");
            lblImage.setForeground(Color.WHITE);
            return;
        }

        int targetWidth = (int) (originalImage.getWidth() * zoomFactor);
        int targetHeight = (int) (originalImage.getHeight() * zoomFactor);

        if (targetWidth > 0 && targetHeight > 0) {
            Image scaled = originalImage.getScaledInstance(targetWidth, targetHeight, Image.SCALE_SMOOTH);
            lblImage.setIcon(new ImageIcon(scaled));
        }
    }

    private void saveImageToDisk() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setSelectedFile(new File(fileName));
        int res = fileChooser.showSaveDialog(this);
        if (res == JFileChooser.APPROVE_OPTION) {
            File target = fileChooser.getSelectedFile();
            try (FileOutputStream fos = new FileOutputStream(target)) {
                fos.write(imageBytes);
                JOptionPane.showMessageDialog(this, "Đã lưu ảnh thành công vào:\n" + target.getAbsolutePath(),
                        "Thành công", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Lỗi khi lưu ảnh: " + ex.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
}
