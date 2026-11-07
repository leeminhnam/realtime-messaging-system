package group_chat.view;

import group_chat.model.GroupItem;

import javax.swing.*;
import java.awt.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GroupListPanel extends JPanel {
    private final String currentUsername;
    private final DefaultListModel<GroupItem> listModel = new DefaultListModel<>();
    private final JList<GroupItem> groupJList = new JList<>(listModel);
    private final JButton btnCreate = new JButton("＋ Tạo nhóm mới");

    // Số tin nhắn chưa xem của từng nhóm (Key: id nhóm)
    private final Map<Integer, Integer> unreadMap = new HashMap<>();

    // Cờ tránh kích hoạt sự kiện chọn nhóm khi mình tự cập nhật danh sách
    private boolean updating = false;
    private GroupSelectListener selectListener;

    @FunctionalInterface
    public interface GroupSelectListener {
        void onGroupSelected(GroupItem group);
    }

    public GroupListPanel(String currentUsername) {
        this.currentUsername = currentUsername;

        setLayout(new BorderLayout(0, 5));
        setBorder(BorderFactory.createTitledBorder("Danh sách nhóm"));

        groupJList.setCellRenderer(new GroupCellRenderer());
        groupJList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        groupJList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && !updating) {
                GroupItem selected = groupJList.getSelectedValue();
                if (selected != null && selectListener != null) {
                    unreadMap.remove(selected.getId());
                    groupJList.repaint();
                    selectListener.onGroupSelected(selected);
                }
            }
        });

        btnCreate.setFocusPainted(false);

        add(btnCreate, BorderLayout.NORTH);
        add(new JScrollPane(groupJList), BorderLayout.CENTER);
    }

    public void setGroupSelectListener(GroupSelectListener listener) {
        this.selectListener = listener;
    }

    public void setCreateListener(Runnable listener) {
        btnCreate.addActionListener(e -> listener.run());
    }

    /**
     * Nạp lại danh sách nhóm và giữ nguyên nhóm đang được chọn (selectedId).
     */
    public void setGroups(List<GroupItem> groups, int selectedId) {
        updating = true;
        listModel.clear();
        for (GroupItem g : groups) {
            listModel.addElement(g);
            if (g.getId() == selectedId) {
                groupJList.setSelectedValue(g, true);
            }
        }
        updating = false;
    }

    /**
     * Tăng số tin nhắn chưa xem của một nhóm (khi có tin mới mà đang không mở nhóm đó).
     */
    public void addUnread(int groupId) {
        unreadMap.merge(groupId, 1, Integer::sum);
        groupJList.repaint();
    }

    // Hiển thị tên nhóm, số thành viên, dấu sao nếu mình là trưởng nhóm, số tin chưa xem
    private class GroupCellRenderer extends JPanel implements ListCellRenderer<GroupItem> {
        private final JLabel lblName = new JLabel();
        private final JLabel lblInfo = new JLabel();

        public GroupCellRenderer() {
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
            setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));

            lblName.setFont(new Font("Segoe UI", Font.BOLD, 13));
            lblInfo.setFont(new Font("Segoe UI", Font.PLAIN, 11));
            lblInfo.setForeground(new Color(130, 130, 130));

            add(lblName);
            add(lblInfo);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends GroupItem> list, GroupItem value, int index,
                boolean isSelected, boolean cellHasFocus) {
            String prefix = value.getLeader().equalsIgnoreCase(currentUsername) ? "★ " : "";
            int unread = unreadMap.getOrDefault(value.getId(), 0);
            String suffix = unread > 0 ? "  (" + unread + " mới)" : "";

            lblName.setText(prefix + value.getName() + suffix);
            lblInfo.setText(value.getMemberCount() + " thành viên");
            setBackground(isSelected ? new Color(225, 238, 255) : Color.WHITE);
            return this;
        }
    }
}
