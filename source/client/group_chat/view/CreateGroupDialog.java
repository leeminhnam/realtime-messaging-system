package group_chat.view;

import account.UserListPanel;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class CreateGroupDialog extends JDialog {

    @FunctionalInterface
    public interface CreateListener {
        void onCreate(String groupName, List<String> members);
    }

    private final JTextField txtName = new JTextField();
    private final List<JCheckBox> checkBoxes = new ArrayList<>();

    public CreateGroupDialog(Frame parent, List<UserListPanel.UserItem> users, CreateListener listener) {
        super(parent, "Tạo nhóm mới", true);
        setSize(380, 460);
        setLocationRelativeTo(parent);
        setLayout(new BorderLayout(8, 8));

        JPanel namePanel = new JPanel(new BorderLayout(0, 4));
        namePanel.setBorder(BorderFactory.createEmptyBorder(10, 12, 0, 12));
        namePanel.add(new JLabel("Tên nhóm:"), BorderLayout.NORTH);
        namePanel.add(txtName, BorderLayout.CENTER);

        JPanel checkPanel = new JPanel();
        checkPanel.setLayout(new BoxLayout(checkPanel, BoxLayout.Y_AXIS));
        for (UserListPanel.UserItem user : users) {
            JCheckBox cb = new JCheckBox(user.getFullName() + " (@" + user.getUsername() + ")");
            cb.setActionCommand(user.getUsername());
            checkBoxes.add(cb);
            checkPanel.add(cb);
        }
        JScrollPane scroll = new JScrollPane(checkPanel);
        scroll.setBorder(BorderFactory.createTitledBorder("Chọn thành viên"));

        JPanel centerPanel = new JPanel(new BorderLayout());
        centerPanel.setBorder(BorderFactory.createEmptyBorder(0, 12, 0, 12));
        centerPanel.add(scroll, BorderLayout.CENTER);

        JButton btnCreate = new JButton("Tạo nhóm");
        JButton btnCancel = new JButton("Hủy");
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.add(btnCancel);
        buttonPanel.add(btnCreate);

        btnCancel.addActionListener(e -> dispose());
        btnCreate.addActionListener(e -> {
            String name = txtName.getText().trim();
            List<String> selected = getSelectedUsernames();

            if (name.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Vui lòng nhập tên nhóm!", "Lỗi", JOptionPane.ERROR_MESSAGE);
                return;
            }
            if (selected.size() < 2) {
                JOptionPane.showMessageDialog(this, "Nhóm cần tối thiểu 3 người, vui lòng chọn ít nhất 2 thành viên!", "Lỗi", JOptionPane.ERROR_MESSAGE);
                return;
            }
            listener.onCreate(name, selected);
            dispose();
        });

        add(namePanel, BorderLayout.NORTH);
        add(centerPanel, BorderLayout.CENTER);
        add(buttonPanel, BorderLayout.SOUTH);
    }

    private List<String> getSelectedUsernames() {
        List<String> result = new ArrayList<>();
        for (JCheckBox cb : checkBoxes) {
            if (cb.isSelected()) {
                result.add(cb.getActionCommand());
            }
        }
        return result;
    }
}
