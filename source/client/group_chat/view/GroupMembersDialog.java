package group_chat.view;

import group_chat.model.MemberItem;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class GroupMembersDialog extends JDialog {

    // Các hành động mà dialog báo lại cho Controller xử lý
    public interface Actions {
        void onAddMember();

        void onRemoveMember(String username);

        void onTransferLeader(String username);

        void onLeaveGroup();

        void onDeleteGroup();
    }

    private final String currentUsername;
    private final DefaultListModel<MemberItem> listModel = new DefaultListModel<>();
    private final JList<MemberItem> memberJList = new JList<>(listModel);
    private final List<MemberItem> members = new ArrayList<>();

    private final JButton btnAdd = new JButton("Thêm thành viên");
    private final JButton btnRemove = new JButton("Xóa khỏi nhóm");
    private final JButton btnTransfer = new JButton("Chuyển quyền trưởng nhóm");
    private final JButton btnLeave = new JButton("Rời nhóm");
    private final JButton btnDelete = new JButton("Xóa nhóm");

    public GroupMembersDialog(Frame parent, String currentUsername, Actions actions) {
        super(parent, "Thành viên nhóm", false);
        this.currentUsername = currentUsername;

        setSize(400, 460);
        setLocationRelativeTo(parent);
        setLayout(new BorderLayout(8, 8));

        memberJList.setCellRenderer(new MemberCellRenderer());
        memberJList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        memberJList.addListSelectionListener(e -> updateButtons());

        JScrollPane scroll = new JScrollPane(memberJList);
        scroll.setBorder(BorderFactory.createEmptyBorder(10, 10, 0, 10));

        JPanel gridPanel = new JPanel(new GridLayout(2, 2, 6, 6));
        gridPanel.add(btnAdd);
        gridPanel.add(btnRemove);
        gridPanel.add(btnTransfer);
        gridPanel.add(btnLeave);

        // Nút xóa nhóm nằm riêng một hàng, chữ đỏ để dễ phân biệt với các nút khác
        btnDelete.setForeground(new Color(200, 35, 51));
        JPanel buttonPanel = new JPanel(new BorderLayout(0, 6));
        buttonPanel.setBorder(BorderFactory.createEmptyBorder(0, 10, 10, 10));
        buttonPanel.add(gridPanel, BorderLayout.CENTER);
        buttonPanel.add(btnDelete, BorderLayout.SOUTH);

        btnAdd.addActionListener(e -> actions.onAddMember());
        btnRemove.addActionListener(e -> actions.onRemoveMember(memberJList.getSelectedValue().getUsername()));
        btnTransfer.addActionListener(e -> actions.onTransferLeader(memberJList.getSelectedValue().getUsername()));
        btnLeave.addActionListener(e -> actions.onLeaveGroup());
        btnDelete.addActionListener(e -> actions.onDeleteGroup());

        add(scroll, BorderLayout.CENTER);
        add(buttonPanel, BorderLayout.SOUTH);
        updateButtons();
    }

    // Nạp lại danh sách thành viên Server gửi về
    public void setMembers(String groupName, List<MemberItem> newMembers) {
        setTitle("Thành viên nhóm: " + groupName);
        members.clear();
        members.addAll(newMembers);

        listModel.clear();
        for (MemberItem m : newMembers) {
            listModel.addElement(m);
        }
        updateButtons();
    }

    public List<MemberItem> getMembers() {
        return members;
    }

    // Kiểm tra mình có phải trưởng nhóm không
    private boolean isCurrentUserLeader() {
        for (MemberItem m : members) {
            if (m.isLeader() && m.getUsername().equalsIgnoreCase(currentUsername)) {
                return true;
            }
        }
        return false;
    }

    // Chỉ trưởng nhóm mới được thêm/xóa/chuyển quyền và xóa nhóm; ai cũng được rời nhóm
    private void updateButtons() {
        boolean leader = isCurrentUserLeader();
        MemberItem selected = memberJList.getSelectedValue();
        boolean canManage = leader && selected != null && !selected.getUsername().equalsIgnoreCase(currentUsername);

        btnAdd.setEnabled(leader);
        btnRemove.setEnabled(canManage);
        btnTransfer.setEnabled(canManage);
        btnLeave.setEnabled(!members.isEmpty());
        btnDelete.setVisible(leader);
    }

    // Hiển thị: chấm trạng thái, họ tên, username và vai trò
    private static class MemberCellRenderer extends JPanel implements ListCellRenderer<MemberItem> {
        private final JLabel lblDot = new JLabel("● ");
        private final JLabel lblName = new JLabel();
        private final JLabel lblRole = new JLabel();

        public MemberCellRenderer() {
            setLayout(new BorderLayout(5, 0));
            setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
            lblDot.setFont(new Font("Segoe UI", Font.BOLD, 18));
            lblName.setFont(new Font("Segoe UI", Font.PLAIN, 13));
            lblRole.setFont(new Font("Segoe UI", Font.BOLD, 11));
            lblRole.setForeground(new Color(220, 120, 0));

            add(lblDot, BorderLayout.WEST);
            add(lblName, BorderLayout.CENTER);
            add(lblRole, BorderLayout.EAST);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends MemberItem> list, MemberItem value, int index,
                boolean isSelected, boolean cellHasFocus) {
            lblName.setText(value.getFullName() + " (@" + value.getUsername() + ")");
            lblRole.setText(value.isLeader() ? "Trưởng nhóm" : "");
            lblDot.setForeground("ONLINE".equalsIgnoreCase(value.getStatus())
                    ? new Color(40, 167, 69)
                    : new Color(160, 160, 160));
            setBackground(isSelected ? new Color(225, 238, 255) : Color.WHITE);
            return this;
        }
    }
}
