package account;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.swing.*;
import java.awt.*;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

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

        new Thread(() -> {
            try (Socket socket = new Socket(host, port);
                    PrintWriter writer = new PrintWriter(socket.getOutputStream(), true);
                    BufferedReader reader = new BufferedReader(
                            new InputStreamReader(socket.getInputStream(), "UTF-8"))) {

                // Đóng gói JSON đăng ký
                JsonObject req = new JsonObject();
                req.addProperty("action", "REGISTER");
                JsonObject data = new JsonObject();
                data.addProperty("username", username);
                data.addProperty("password", password);
                data.addProperty("fullName", fullName);
                req.add("data", data);

                // Gửi sang Server
                writer.println(req.toString());

                // Đọc phản hồi
                String responseStr = reader.readLine();
                if (responseStr != null) {
                    JsonObject res = JsonParser.parseString(responseStr).getAsJsonObject();
                    String status = res.get("status").getAsString();
                    String message = res.get("message").getAsString();

                    SwingUtilities.invokeLater(() -> {
                        if ("SUCCESS".equalsIgnoreCase(status)) {
                            JOptionPane.showMessageDialog(this, message, "Thành công", JOptionPane.INFORMATION_MESSAGE);
                            dispose();
                        } else {
                            JOptionPane.showMessageDialog(this, message, "Lỗi đăng ký", JOptionPane.ERROR_MESSAGE);
                        }
                    });
                }
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(this, "Không thể kết nối tới Server: " + ex.getMessage(),
                            "Lỗi kết nối", JOptionPane.ERROR_MESSAGE);
                });
            }
        }).start();
    }
}