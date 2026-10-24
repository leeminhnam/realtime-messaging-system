import account.ProfileDialog;
import account.UserListPanel;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.swing.*;
import java.awt.*;
import java.io.BufferedReader;
import java.io.PrintWriter;
import java.net.Socket;

public class MainFrame extends JFrame {
    private final Socket socket;
    private final BufferedReader reader;
    private final PrintWriter writer;
    private final JsonObject currentUser;

    private JLabel lblUserInfo;
    private UserListPanel userListPanel;

    public MainFrame(Socket socket, BufferedReader reader, PrintWriter writer, JsonObject currentUser) {
        this.socket = socket;
        this.reader = reader;
        this.writer = writer;
        this.currentUser = currentUser;

        updateWindowTitle();
        setSize(850, 600);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        initUI();
        startListeningServer();
    }

    private void updateWindowTitle() {
        String fullName = currentUser.has("fullName") ? currentUser.get("fullName").getAsString() : "";
        String username = currentUser.has("username") ? currentUser.get("username").getAsString() : "";
        setTitle("Hệ thống nhắn tin - " + fullName + " (@" + username + ")");
    }

    private void initUI() {
        JPanel mainPanel = new JPanel(new BorderLayout());

        // Header phía trên
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBorder(BorderFactory.createEmptyBorder(10, 15, 10, 15));
        headerPanel.setBackground(new Color(240, 242, 245));

        String fullName = currentUser.get("fullName").getAsString();
        String username = currentUser.get("username").getAsString();

        lblUserInfo = new JLabel("Xin chào, " + fullName + " (@" + username + ")");
        lblUserInfo.setFont(new Font("Segoe UI", Font.BOLD, 14));

        JPanel rightHeaderPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        rightHeaderPanel.setOpaque(false);

        JButton btnProfile = new JButton("Hồ sơ cá nhân");
        JButton btnLogout = new JButton("Đăng xuất");

        // Mở Cửa sổ Hồ sơ cá nhân
        btnProfile.addActionListener(e -> {
            new ProfileDialog(this, writer, currentUser, () -> {
                updateUserInfoDisplay();
            }).setVisible(true);
        });

        btnLogout.addActionListener(e -> {
            try {
                socket.close();
            } catch (Exception ignored) {
            }
            dispose();
            System.exit(0);
        });

        rightHeaderPanel.add(btnProfile);
        rightHeaderPanel.add(btnLogout);

        headerPanel.add(lblUserInfo, BorderLayout.WEST);
        headerPanel.add(rightHeaderPanel, BorderLayout.EAST);
        mainPanel.add(headerPanel, BorderLayout.NORTH);

        // Sidebar bên trái
        userListPanel = new UserListPanel(currentUser.get("username").getAsString());
        if (currentUser.has("allUsers")) {
            JsonArray usersArray = currentUser.getAsJsonArray("allUsers");
            userListPanel.loadUserList(usersArray);
        }

        userListPanel.setPreferredSize(new Dimension(280, 0));
        mainPanel.add(userListPanel, BorderLayout.WEST);

        // Khung chat trống
        JPanel chatArea = new JPanel(new GridBagLayout());
        JLabel lblPlaceholder = new JLabel("Chọn một người dùng ở danh sách bên trái để bắt đầu chat");
        lblPlaceholder.setForeground(Color.GRAY);
        chatArea.add(lblPlaceholder);

        mainPanel.add(chatArea, BorderLayout.CENTER);

        setContentPane(mainPanel);
    }

    private void updateUserInfoDisplay() {
        String fullName = currentUser.get("fullName").getAsString();
        String username = currentUser.get("username").getAsString();
        lblUserInfo.setText("Xin chào, " + fullName + " (@" + username + ")");
        updateWindowTitle();
    }

    private void startListeningServer() {
        new Thread(() -> {
            try {
                String line;
                while ((line = reader.readLine()) != null) {
                    JsonObject msg = JsonParser.parseString(line).getAsJsonObject();
                    String action = msg.has("action") ? msg.get("action").getAsString() : "";

                    // Cập nhật trạng thái Online/Offline
                    if ("USER_STATUS_CHANGE".equalsIgnoreCase(action)) {
                        JsonObject data = msg.getAsJsonObject("data");
                        String username = data.get("username").getAsString();
                        String status = data.get("status").getAsString();

                        SwingUtilities.invokeLater(() -> {
                            userListPanel.updateUserStatus(username, status);
                        });
                    }
                    // Phản hồi khi sửa Hồ sơ thành công
                    else if ("UPDATE_PROFILE_RESPONSE".equalsIgnoreCase(action)) {
                        String status = msg.get("status").getAsString();
                        String message = msg.get("message").getAsString();
                        SwingUtilities.invokeLater(() -> {
                            if ("SUCCESS".equalsIgnoreCase(status)) {
                                JOptionPane.showMessageDialog(this, message, "Thành công",
                                        JOptionPane.INFORMATION_MESSAGE);
                                updateUserInfoDisplay();
                            } else {
                                JOptionPane.showMessageDialog(this, message, "Lỗi", JOptionPane.ERROR_MESSAGE);
                            }
                        });
                    }
                }
            } catch (Exception e) {
                System.out.println("[CLIENT] Đã ngắt kết nối với Server.");
            }
        }).start();
    }
}