package account;

import com.google.gson.JsonObject;

import javax.swing.*;
import java.awt.*;
import java.io.PrintWriter;

public class ProfileDialog extends JDialog {
    private final PrintWriter writer;
    private final JsonObject currentUser;

    private JTextField txtUsername;
    private JTextField txtFullName;
    private final Runnable onProfileUpdated;

    public ProfileDialog(Frame parent, PrintWriter writer, JsonObject currentUser, Runnable onProfileUpdated) {
        super(parent, "Hồ sơ cá nhân", true);
        this.writer = writer;
        this.currentUser = currentUser;
        this.onProfileUpdated = onProfileUpdated;

        setSize(380, 230);
        setLocationRelativeTo(parent);
        setResizable(false);

        initUI();
    }

    private void initUI() {
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        JLabel lblTitle = new JLabel("CẬP NHẬT HỒ SƠ", SwingConstants.CENTER);
        lblTitle.setFont(new Font("Segoe UI", Font.BOLD, 16));
        lblTitle.setForeground(new Color(33, 150, 243));
        mainPanel.add(lblTitle, BorderLayout.NORTH);

        JPanel formPanel = new JPanel(new GridLayout(2, 2, 10, 12));

        String username = currentUser.has("username") ? currentUser.get("username").getAsString() : "";
        String fullName = currentUser.has("fullName") ? currentUser.get("fullName").getAsString() : "";

        txtUsername = new JTextField(username);
        txtUsername.setEditable(false);

        txtFullName = new JTextField(fullName);

        formPanel.add(new JLabel("Tên đăng nhập:"));
        formPanel.add(txtUsername);
        formPanel.add(new JLabel("Họ và tên:"));
        formPanel.add(txtFullName);

        mainPanel.add(formPanel, BorderLayout.CENTER);

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 0));
        JButton btnSave = new JButton("Lưu");
        JButton btnCancel = new JButton("Hủy");

        btnSave.setPreferredSize(new Dimension(90, 32));
        btnCancel.setPreferredSize(new Dimension(90, 32));

        btnSave.addActionListener(e -> handleSaveProfile());
        btnCancel.addActionListener(e -> dispose());

        btnPanel.add(btnSave);
        btnPanel.add(btnCancel);
        mainPanel.add(btnPanel, BorderLayout.SOUTH);

        setContentPane(mainPanel);
    }

    private void handleSaveProfile() {
        String newFullName = txtFullName.getText().trim();

        if (newFullName.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Họ và tên không được để trống!", "Cảnh báo",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        JsonObject req = new JsonObject();
        req.addProperty("action", "UPDATE_PROFILE");
        JsonObject data = new JsonObject();
        data.addProperty("fullName", newFullName);
        data.addProperty("avatar", ""); // chưa hỗ trợ đổi avatar
        req.add("data", data);

        writer.println(req.toString());

        currentUser.addProperty("fullName", newFullName);

        if (onProfileUpdated != null) {
            onProfileUpdated.run();
        }

        dispose();
    }
}