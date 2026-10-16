import javax.swing.*;
import java.awt.*;

/**
 * Điểm khởi chạy của Client - Hệ thống nhắn tin thời gian thực (Java Swing)
 */
public class ClientMain {

    public static void main(String[] args) {
        // Thiết lập giao diện theo Look & Feel của hệ điều hành
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            System.err.println("Không thể thiết lập System LookAndFeel: " + e.getMessage());
        }

        // Chạy giao diện Swing trên Event Dispatch Thread (EDT)
        SwingUtilities.invokeLater(() -> {
            showLoginWindow();
        });
    }

    /**
     * Hiển thị cửa sổ đăng nhập ban đầu
     */
    private static void showLoginWindow() {
        JFrame frame = new JFrame("Hệ thống nhắn tin thời gian thực - Đăng nhập");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(420, 320);
        frame.setLocationRelativeTo(null); // Hiển thị giữa màn hình
        frame.setResizable(false);

        // Panel chính
        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(20, 25, 20, 25));

        // Tiêu đề
        JLabel lblTitle = new JLabel("ĐĂNG NHẬP HỆ THỐNG", SwingConstants.CENTER);
        lblTitle.setFont(new Font("Segoe UI", Font.BOLD, 18));
        lblTitle.setForeground(new Color(33, 150, 243));
        mainPanel.add(lblTitle, BorderLayout.NORTH);

        // Form nhập liệu
        JPanel formPanel = new JPanel(new GridLayout(4, 2, 8, 12));

        JLabel lblHost = new JLabel("Server Host:");
        JTextField txtHost = new JTextField("localhost");

        JLabel lblPort = new JLabel("Port:");
        JTextField txtPort = new JTextField("8888");

        JLabel lblUsername = new JLabel("Tên đăng nhập:");
        JTextField txtUsername = new JTextField();

        JLabel lblPassword = new JLabel("Mật khẩu:");
        JPasswordField txtPassword = new JPasswordField();

        formPanel.add(lblHost);
        formPanel.add(txtHost);
        formPanel.add(lblPort);
        formPanel.add(txtPort);
        formPanel.add(lblUsername);
        formPanel.add(txtUsername);
        formPanel.add(lblPassword);
        formPanel.add(txtPassword);

        mainPanel.add(formPanel, BorderLayout.CENTER);

        // Nút bấm đăng nhập / đăng ký
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 0));
        JButton btnLogin = new JButton("Đăng nhập");
        JButton btnRegister = new JButton("Đăng ký");

        btnLogin.setPreferredSize(new Dimension(110, 32));
        btnRegister.setPreferredSize(new Dimension(110, 32));

        btnLogin.addActionListener(e -> {
            String host = txtHost.getText().trim();
            String port = txtPort.getText().trim();
            String username = txtUsername.getText().trim();
            String password = new String(txtPassword.getPassword());

            if (username.isEmpty() || password.isEmpty()) {
                JOptionPane.showMessageDialog(frame, "Vui lòng nhập đầy đủ tên đăng nhập và mật khẩu!",
                        "Cảnh báo", JOptionPane.WARNING_MESSAGE);
                return;
            }

            // TODO: Kết nối tới ServerSocket qua host:port
            // Gửi yêu cầu đăng nhập tới module `account`
            JOptionPane.showMessageDialog(frame,
                "Đang kết nối tới " + host + ":" + port + " với tài khoản: " + username +
                "\n(Chức năng sẽ được tích hợp với module account)",
                "Thông báo", JOptionPane.INFORMATION_MESSAGE);
        });

        btnRegister.addActionListener(e -> {
            // TODO: Mở form đăng ký của module `account`
            JOptionPane.showMessageDialog(frame, "Chuyển sang màn hình Đăng ký tài khoản (module account)",
                    "Thông báo", JOptionPane.INFORMATION_MESSAGE);
        });

        buttonPanel.add(btnLogin);
        buttonPanel.add(btnRegister);
        mainPanel.add(buttonPanel, BorderLayout.SOUTH);

        frame.setContentPane(mainPanel);
        frame.setVisible(true);
    }
}
