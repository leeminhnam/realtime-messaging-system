import account.ProfileDialog;
import account.UserListPanel;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import file_notification.controller.FileClientManager;
import file_notification.notification.NotificationToast;
import group_chat.controller.GroupChatController;
import group_chat.view.GroupChatPanel;
import group_chat.view.GroupListPanel;
import private_chat.controller.PrivateChatController;
import private_chat.model.ChatPartner;
import private_chat.view.PrivateChatPanel;

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
    private PrivateChatPanel privateChatPanel;
    private PrivateChatController privateChatController;
    private GroupListPanel groupListPanel;
    private GroupChatPanel groupChatPanel;
    private GroupChatController groupChatController;

    public MainFrame(Socket socket, BufferedReader reader, PrintWriter writer, JsonObject currentUser) {
        this.socket = socket;
        this.reader = reader;
        this.writer = writer;
        this.currentUser = currentUser;

        // Khởi tạo FileClientManager với luồng ghi mạng
        FileClientManager.getInstance().setWriter(writer);

        updateWindowTitle();
        setSize(980, 680);
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
        userListPanel = new UserListPanel(username);
        if (currentUser.has("allUsers")) {
            JsonArray usersArray = currentUser.getAsJsonArray("allUsers");
            userListPanel.loadUserList(usersArray);
        }

        groupListPanel = new GroupListPanel(username);

        // Sidebar có 2 tab: danh sách người dùng (chat riêng) và danh sách nhóm (chat nhóm)
        JTabbedPane sidebarTabs = new JTabbedPane();
        sidebarTabs.addTab("Người dùng", userListPanel);
        sidebarTabs.addTab("Nhóm", groupListPanel);
        sidebarTabs.setPreferredSize(new Dimension(280, 0));
        mainPanel.add(sidebarTabs, BorderLayout.WEST);

        // Vùng trung tâm: chuyển qua lại giữa khung Private Chat và khung Group Chat
        privateChatPanel = new PrivateChatPanel();
        privateChatController = new PrivateChatController(privateChatPanel, writer, username);

        groupChatPanel = new GroupChatPanel();
        groupChatController = new GroupChatController(this, groupListPanel, groupChatPanel, userListPanel, writer, username);

        CardLayout centerLayout = new CardLayout();
        JPanel centerPanel = new JPanel(centerLayout);
        centerPanel.add(privateChatPanel, "PRIVATE");
        centerPanel.add(groupChatPanel, "GROUP");
        mainPanel.add(centerPanel, BorderLayout.CENTER);

        // Đổi tab ở sidebar thì đổi khung chat tương ứng
        sidebarTabs.addChangeListener(e -> {
            centerLayout.show(centerPanel, sidebarTabs.getSelectedIndex() == 0 ? "PRIVATE" : "GROUP");
        });

        // Lắng nghe sự kiện click chọn một người dùng từ danh bạ bên trái
        userListPanel.setUserSelectListener(userItem -> {
            ChatPartner partner = new ChatPartner(userItem.getUsername(), userItem.getFullName(), userItem.getStatus());
            privateChatController.openChatWith(partner);
        });

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

                    // 1. Cập nhật trạng thái Online/Offline
                    if ("USER_STATUS_CHANGE".equalsIgnoreCase(action)) {
                        JsonObject data = msg.getAsJsonObject("data");
                        String user = data.get("username").getAsString();
                        String status = data.get("status").getAsString();
                        String userFullName = data.has("fullName") ? data.get("fullName").getAsString() : null;

                        SwingUtilities.invokeLater(() -> {
                            userListPanel.updateUserStatus(user, status, userFullName);
                            privateChatController.onPartnerStatusChanged(user, status);
                        });
                    }
                    // 2. Nhận tin nhắn chat riêng / media thời gian thực
                    else if ("RECEIVE_PRIVATE_MESSAGE".equalsIgnoreCase(action)) {
                        if (msg.has("data")) {
                            JsonObject data = msg.getAsJsonObject("data");
                            privateChatController.onMessageReceived(data);
                        }
                    }
                    // 3. Phản hồi khi gửi tin nhắn chữ thành công
                    else if ("SEND_PRIVATE_MESSAGE_RESPONSE".equalsIgnoreCase(action)) {
                        String status = msg.has("status") ? msg.get("status").getAsString() : "";
                        if ("SUCCESS".equalsIgnoreCase(status) && msg.has("data")) {
                            JsonObject data = msg.getAsJsonObject("data");
                            privateChatController.onSendMessageSuccess(data);
                        } else {
                            String message = msg.has("message") ? msg.get("message").getAsString() : "Lỗi gửi tin nhắn";
                            SwingUtilities.invokeLater(() -> {
                                JOptionPane.showMessageDialog(this, message, "Lỗi", JOptionPane.ERROR_MESSAGE);
                            });
                        }
                    }
                    // 4. Phản hồi khi gửi tệp thành công (Module 4)
                    else if ("SEND_FILE_RESPONSE".equalsIgnoreCase(action)) {
                        String status = msg.has("status") ? msg.get("status").getAsString() : "";
                        if ("SUCCESS".equalsIgnoreCase(status) && msg.has("data")) {
                            JsonObject data = msg.getAsJsonObject("data");
                            privateChatController.onSendMessageSuccess(data);
                        } else {
                            String message = msg.has("message") ? msg.get("message").getAsString() : "Lỗi gửi tệp";
                            SwingUtilities.invokeLater(() -> {
                                JOptionPane.showMessageDialog(this, message, "Lỗi", JOptionPane.ERROR_MESSAGE);
                            });
                        }
                    }
                    // 5. Phản hồi khi tải dữ liệu tệp (Module 4)
                    else if ("DOWNLOAD_FILE_RESPONSE".equalsIgnoreCase(action)) {
                        String status = msg.has("status") ? msg.get("status").getAsString() : "";
                        if ("SUCCESS".equalsIgnoreCase(status) && msg.has("data")) {
                            JsonObject data = msg.getAsJsonObject("data");
                            FileClientManager.getInstance().onDownloadResponse(data);
                        }
                    }
                    // 6. Nhận thông báo nổi thời gian thực (Module 4: Toast Notification + Sound)
                    else if ("RECEIVE_NOTIFICATION".equalsIgnoreCase(action)) {
                        if (msg.has("data")) {
                            JsonObject data = msg.getAsJsonObject("data");
                            String notifType = data.has("fileType") ? data.get("fileType").getAsString() : (data.has("type") ? data.get("type").getAsString() : "TEXT");
                            String senderUser = data.get("sender").getAsString();
                            String senderFullName = data.has("senderFullName") ? data.get("senderFullName").getAsString() : senderUser;
                            String notifMessage = data.get("message").getAsString();

                            SwingUtilities.invokeLater(() -> {
                                NotificationToast.showNotification(this, notifType, senderUser, senderFullName, notifMessage, targetSender -> {
                                    // Khi nhấn vào thông báo: tự động chọn hội thoại đó
                                    userListPanel.selectUser(targetSender);
                                });
                            });
                        }
                    }
                    // 7. Nhận lịch sử tin nhắn cuộc trò chuyện
                    else if ("GET_CHAT_HISTORY_RESPONSE".equalsIgnoreCase(action)) {
                        if (msg.has("data")) {
                            JsonObject data = msg.getAsJsonObject("data");
                            privateChatController.onChatHistoryReceived(data);
                        }
                    }
                    // 8. Các gói tin của Module 3: Chat nhóm (tạo nhóm, thành viên, tin nhắn nhóm...)
                    else if (GroupChatController.isGroupAction(action)) {
                        groupChatController.handleServerMessage(msg);
                    }
                    // 9. Phản hồi khi sửa Hồ sơ thành công
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
