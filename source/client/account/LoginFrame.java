package account;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.swing.*;
import java.awt.*;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class LoginFrame extends JFrame {

    @FunctionalInterface
    public interface LoginSuccessCallback {
        void onLoginSuccess(Socket socket, BufferedReader reader, PrintWriter writer, JsonObject userData);
    }

    private JTextField txtHost;
    private JTextField txtPort;
    private JTextField txtUsername;
    private JPasswordField txtPassword;

    private final LoginSuccessCallback loginSuccessCallback;

    public LoginFrame(LoginSuccessCallback loginSuccessCallback) {
        this.loginSuccessCallback = loginSuccessCallback;

        setTitle("Hệ thống nhắn tin thời gian thực - Đăng nhập");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(420, 340);
        setLocationRelativeTo(null);
        setResizable(false);

        initUI();
    }

    private void initUI() {
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(20, 25, 20, 25));

        JLabel lblTitle = new JLabel("ĐĂNG NHẬP HỆ THỐNG", SwingConstants.CENTER);
        lblTitle.setFont(new Font("Segoe UI", Font.BOLD, 18));
        lblTitle.setForeground(new Color(33, 150, 243));
        mainPanel.add(lblTitle, BorderLayout.NORTH);

        JPanel formPanel = new JPanel(new GridLayout(4, 2, 8, 12));

        txtHost = new JTextField("localhost");
        txtPort = new JTextField("8888");
        txtUsername = new JTextField();
        txtPassword = new JPasswordField();

        formPanel.add(new JLabel("Server Host:"));
        formPanel.add(txtHost);
        formPanel.add(new JLabel("Port:"));
        formPanel.add(txtPort);
        formPanel.add(new JLabel("Tên đăng nhập:"));
        formPanel.add(txtUsername);
        formPanel.add(new JLabel("Mật khẩu:"));
        formPanel.add(txtPassword);

        mainPanel.add(formPanel, BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 0));
        JButton btnLogin = new JButton("Đăng nhập");
        JButton btnRegister = new JButton("Đăng ký");

        btnLogin.setPreferredSize(new Dimension(110, 32));
        btnRegister.setPreferredSize(new Dimension(110, 32));

        btnRegister.addActionListener(e -> handleOpenRegister());
        btnLogin.addActionListener(e -> handleLogin());

        buttonPanel.add(btnLogin);
        buttonPanel.add(btnRegister);
        mainPanel.add(buttonPanel, BorderLayout.SOUTH);

        setContentPane(mainPanel);
    }

    private void handleOpenRegister() {
        try {
            String host = txtHost.getText().trim();
            int port = Integer.parseInt(txtPort.getText().trim());
            new RegisterFrame(host, port).setVisible(true);
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Cổng Port không hợp lệ!", "Lỗi", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void handleLogin() {
        String host = txtHost.getText().trim();
        String portStr = txtPort.getText().trim();
        String username = txtUsername.getText().trim();
        String password = new String(txtPassword.getPassword());

        if (username.isEmpty() || password.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Vui lòng nhập tên đăng nhập và mật khẩu!", "Cảnh báo",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        new Thread(() -> {
            try {
                int port = Integer.parseInt(portStr);
                Socket socket = new Socket(host, port);
                PrintWriter writer = new PrintWriter(socket.getOutputStream(), true);
                BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));

                JsonObject req = new JsonObject();
                req.addProperty("action", "LOGIN");
                JsonObject data = new JsonObject();
                data.addProperty("username", username);
                data.addProperty("password", password);
                req.add("data", data);

                writer.println(req.toString());

                String resStr = reader.readLine();
                if (resStr != null) {
                    JsonObject res = JsonParser.parseString(resStr).getAsJsonObject();
                    String status = res.get("status").getAsString();
                    String message = res.get("message").getAsString();

                    SwingUtilities.invokeLater(() -> {
                        if ("SUCCESS".equalsIgnoreCase(status)) {
                            JsonObject userData = res.getAsJsonObject("data");
                            dispose(); // Đóng LoginFrame

                            if (loginSuccessCallback != null) {
                                loginSuccessCallback.onLoginSuccess(socket, reader, writer, userData);
                            }
                        } else {
                            JOptionPane.showMessageDialog(this, message, "Đăng nhập thất bại",
                                    JOptionPane.ERROR_MESSAGE);
                        }
                    });
                }
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(this, "Lỗi kết nối Server: " + ex.getMessage(), "Lỗi",
                            JOptionPane.ERROR_MESSAGE);
                });
            }
        }).start();
    }
}