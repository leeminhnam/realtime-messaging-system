package account.view;

import account.model.UserItem;
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
    // Số tin chưa xem trong phiên hiện tại, theo username người gửi
    private final Map<String, Integer> unreadMap = new HashMap<>();

    public UserListPanel(String currentUsername) {
        this.currentUsername = currentUsername;
        this.listModel = new DefaultListModel<>();
        this.userJList = new JList<UserItem>(listModel) {
            @Override
            public boolean getScrollableTracksViewportWidth() {
                return true;
            }
        };

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

    // Đổi chấm trạng thái khi nhận USER_STATUS_CHANGE
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
            // Người mới đăng ký chưa có trong danh bạ thì thêm vào
            String displayName = (fullName != null && !fullName.isEmpty()) ? fullName : username;
            UserItem newItem = new UserItem(username, displayName, status);
            userMap.put(username.toLowerCase(), newItem);
            listModel.addElement(newItem);
        }
    }

    public void updateUserStatus(String username, String status) {
        updateUserStatus(username, status, null);
    }

    public void addUnread(String username) {
        unreadMap.merge(username.toLowerCase(), 1, Integer::sum);
        userJList.repaint();
    }

    public void clearUnread(String username) {
        unreadMap.remove(username.toLowerCase());
        userJList.repaint();
    }

    // Chọn người dùng theo username, dùng khi bấm vào thông báo
    public void selectUser(String username) {
        if (username == null) return;
        UserItem item = userMap.get(username.toLowerCase());
        if (item != null) {
            userJList.setSelectedValue(item, true);
        }
    }

    // Danh bạ hiện tại, dùng khi tạo nhóm hoặc thêm thành viên
    public java.util.List<UserItem> getUsers() {
        return java.util.Collections.list(listModel.elements());
    }

    // Vẽ chấm xanh cho người online, xám cho người offline
    private class UserCellRenderer extends JPanel implements ListCellRenderer<UserItem> {
        private final JLabel lblName = new JLabel();
        private final JLabel lblStatusDot = new JLabel("● ");
        private final JLabel lblUnread = new JLabel();

        public UserCellRenderer() {
            setLayout(new BorderLayout(5, 0));
            setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10)); // padding cho thoáng

            // Chấm to hơn một chút cho dễ nhìn
            lblStatusDot.setFont(new Font("Segoe UI", Font.BOLD, 18));

            lblUnread.setFont(new Font("Segoe UI", Font.BOLD, 12));
            lblUnread.setForeground(new Color(0, 102, 204));
            // Giữ chỗ riêng cho bộ đếm, không để tên dài đẩy nhãn ra ngoài sidebar
            add(lblStatusDot, BorderLayout.WEST);
            add(lblName, BorderLayout.CENTER);
            add(lblUnread, BorderLayout.EAST);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends UserItem> list, UserItem value, int index,
                boolean isSelected, boolean cellHasFocus) {
            int unread = unreadMap.getOrDefault(value.getUsername().toLowerCase(), 0);
            lblUnread.setText(unread > 0 ? "(" + unread + " mới)" : "");
            lblUnread.setVisible(unread > 0);
            lblName.setText(value.getFullName() + " (@" + value.getUsername() + ")");
            setToolTipText(lblName.getText());
            lblName.setFont(new Font("Segoe UI", Font.PLAIN, 13));

            if ("ONLINE".equalsIgnoreCase(value.getStatus())) {
                lblStatusDot.setForeground(new Color(40, 167, 69)); // xanh lá
            } else {
                lblStatusDot.setForeground(new Color(160, 160, 160)); // xám
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
