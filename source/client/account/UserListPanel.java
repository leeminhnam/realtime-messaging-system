package account;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import javax.swing.*;
import java.awt.*;
import java.util.HashMap;
import java.util.Map;

public class UserListPanel extends JPanel {
    private final String currentUsername;
    private final DefaultListModel<UserItem> listModel;
    private final JList<UserItem> userJList;
    private final Map<String, UserItem> userMap = new HashMap<>();

    public UserListPanel(String currentUsername) {
        this.currentUsername = currentUsername;
        this.listModel = new DefaultListModel<>();
        this.userJList = new JList<>(listModel);

        setLayout(new BorderLayout());
        setBorder(BorderFactory.createTitledBorder("Danh sách người dùng"));

        userJList.setCellRenderer(new UserCellRenderer());
        userJList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        add(new JScrollPane(userJList), BorderLayout.CENTER);
    }

    @FunctionalInterface
    public interface UserSelectListener {
        void onUserSelected(UserItem user);
    }

    public void setUserSelectListener(UserSelectListener listener) {
        userJList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                UserItem selected = userJList.getSelectedValue();
                if (selected != null && listener != null) {
                    listener.onUserSelected(selected);
                }
            }
        });
    }

    public void loadUserList(JsonArray usersArray) {
        listModel.clear();
        userMap.clear();

        for (JsonElement elem : usersArray) {
            JsonObject obj = elem.getAsJsonObject();
            String username = obj.get("username").getAsString();
            String fullName = obj.get("fullName").getAsString();
            String status = obj.get("status").getAsString();

            // Không hiển thị chính tài khoản đang đăng nhập trong danh sách
            if (!username.equalsIgnoreCase(currentUsername)) {
                UserItem item = new UserItem(username, fullName, status);
                userMap.put(username.toLowerCase(), item);
                listModel.addElement(item);
            }
        }
    }

    // Cập nhật trạng thái chấm Online/Offline thời gian thực
    public void updateUserStatus(String username, String status, String fullName) {
        UserItem item = userMap.get(username.toLowerCase());
        if (item != null) {
            item.setStatus(status);
            int index = listModel.indexOf(item);
            if (index != -1) {
                listModel.set(index, item);
            }
            userJList.repaint();
        } else if ("ONLINE".equalsIgnoreCase(status) && !username.equalsIgnoreCase(currentUsername)) {
            // User mới đăng ký và vừa đăng nhập, tự động thêm mới vào danh bạ
            String displayName = (fullName != null && !fullName.isEmpty()) ? fullName : username;
            UserItem newItem = new UserItem(username, displayName, status);
            userMap.put(username.toLowerCase(), newItem);
            listModel.addElement(newItem);
        }
    }

    public void updateUserStatus(String username, String status) {
        updateUserStatus(username, status, null);
    }

    // Model dữ liệu từng item
    public static class UserItem {
        private final String username;
        private final String fullName;
        private String status;

        public UserItem(String username, String fullName, String status) {
            this.username = username;
            this.fullName = fullName;
            this.status = status;
        }

        public String getUsername() {
            return username;
        }

        public String getFullName() {
            return fullName;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }
    }

    // Custom Renderer hiển thị chấm Xanh (ONLINE) / Xám (OFFLINE)
    private static class UserCellRenderer extends JPanel implements ListCellRenderer<UserItem> {
        private final JLabel lblName = new JLabel();
        private final JLabel lblStatusDot = new JLabel("● ");

        public UserCellRenderer() {
            setLayout(new BorderLayout(5, 0));
            setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10)); // Tăng padding dòng cho thoáng

            // Tăng kích thước chấm tròn trạng thái
            lblStatusDot.setFont(new Font("Segoe UI", Font.BOLD, 18));

            add(lblStatusDot, BorderLayout.WEST);
            add(lblName, BorderLayout.CENTER);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends UserItem> list, UserItem value, int index,
                boolean isSelected, boolean cellHasFocus) {
            lblName.setText(value.getFullName() + " (@" + value.getUsername() + ")");
            lblName.setFont(new Font("Segoe UI", Font.PLAIN, 13));

            if ("ONLINE".equalsIgnoreCase(value.getStatus())) {
                lblStatusDot.setForeground(new Color(40, 167, 69)); // Xanh lá nổi bật hơn
            } else {
                lblStatusDot.setForeground(new Color(160, 160, 160)); // Xám
            }

            if (isSelected) {
                setBackground(new Color(225, 238, 255));
            } else {
                setBackground(Color.WHITE);
            }

            return this;
        }
    }
}