package account.view;

import account.controller.AccountController;

import javax.swing.*;
import java.awt.*;

public class RegisterFrame extends JFrame {
    private final String host;
    private final int port;

    private JTextField txtUsername;
    private JTextField txtFullName;
    private JPasswordField txtPassword;
    private JPasswordField txtConfirmPassword;

    public RegisterFrame(String host, int port) {
        this.host = host;
        this.port = port;

        setTitle("Hệ thống nhắn tin - Đăng ký tài khoản");
        setSize(420, 380);
        setLocationRelativeTo(null);
        setResizable(false);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        initUI();
    }

    private void initUI() {
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(20, 25, 20, 25));

        JLabel lblTitle = new JLabel("ĐĂNG KÝ TÀI KHOẢN MỚI", SwingConstants.CENTER);
        lblTitle.setFont(new Font("Segoe UI", Font.BOLD, 18));
        lblTitle.setForeground(new Color(33, 150, 243));
        mainPanel.add(lblTitle, BorderLayout.NORTH);

        JPanel formPanel = new JPanel(new GridLayout(4, 2, 8, 12));

        formPanel.add(new JLabel("Họ và tên:"));
        txtFullName = new JTextField();
        formPanel.add(txtFullName);

        formPanel.add(new JLabel("Tên đăng nhập:"));
        txtUsername = new JTextField();
        formPanel.add(txtUsername);

        formPanel.add(new JLabel("Mật khẩu:"));
        txtPassword = new JPasswordField();
        formPanel.add(txtPassword);

        formPanel.add(new JLabel("Xác nhận mật khẩu:"));
        txtConfirmPassword = new JPasswordField();
        formPanel.add(txtConfirmPassword);

        mainPanel.add(formPanel, BorderLayout.CENTER);

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 0));
        JButton btnRegister = new JButton("Đăng ký");
        JButton btnCancel = new JButton("Hủy");

        btnRegister.setPreferredSize(new Dimension(100, 32));
        btnCancel.setPreferredSize(new Dimension(100, 32));

        btnRegister.addActionListener(e -> handleRegister());
        btnCancel.addActionListener(e -> dispose());

        btnPanel.add(btnRegister);
        btnPanel.add(btnCancel);
        mainPanel.add(btnPanel, BorderLayout.SOUTH);

        setContentPane(mainPanel);
    }

    private void handleRegister() {
        String fullName = txtFullName.getText().trim();
        String username = txtUsername.getText().trim();
        String password = new String(txtPassword.getPassword());
        String confirmPw = new String(txtConfirmPassword.getPassword());

        if (fullName.isEmpty() || username.isEmpty() || password.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Vui lòng nhập đầy đủ thông tin!", "Cảnh báo",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        if (!password.equals(confirmPw)) {
            JOptionPane.showMessageDialog(this, "Mật khẩu xác nhận không khớp!", "Lỗi", JOptionPane.ERROR_MESSAGE);
            return;
        }

        // Việc mở Socket và gửi/nhận JSON được xử lý trong Controller.
        AccountController.registerAsync(host, port, username, password, fullName,
                (status, message) -> {
                    if ("SUCCESS".equalsIgnoreCase(status)) {
                        JOptionPane.showMessageDialog(this, message, "Thành công", JOptionPane.INFORMATION_MESSAGE);
                        dispose();
                    } else {
                        JOptionPane.showMessageDialog(this, message, "Lỗi đăng ký", JOptionPane.ERROR_MESSAGE);
                    }
                },
                ex -> JOptionPane.showMessageDialog(this, "Không thể kết nối tới Server: " + ex.getMessage(),
                        "Lỗi kết nối", JOptionPane.ERROR_MESSAGE));
    }
}