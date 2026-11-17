package group_chat.controller;

import account.UserListPanel;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import file_notification.controller.FileClientManager;
import group_chat.model.GroupItem;
import group_chat.model.MemberItem;
import group_chat.view.CreateGroupDialog;
import group_chat.view.GroupChatPanel;
import group_chat.view.GroupListPanel;
import group_chat.view.GroupMembersDialog;
import private_chat.model.ChatMessage;

import javax.swing.*;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

public class GroupChatController {
    private static final Gson gson = new Gson();

    private final JFrame parent;
    private final GroupListPanel listView;
    private final GroupChatPanel chatView;
    private final UserListPanel userListPanel;
    private final PrintWriter writer;
    private final String currentUsername;

    private GroupMembersDialog membersDialog;
    private int pendingOpenGroupId = 0; // Nhóm vừa tạo, mở ngay khi danh sách nhóm được tải về

    public GroupChatController(JFrame parent, GroupListPanel listView, GroupChatPanel chatView,
            UserListPanel userListPanel, PrintWriter writer, String currentUsername) {
        this.parent = parent;
        this.listView = listView;
        this.chatView = chatView;
        this.userListPanel = userListPanel;
        this.writer = writer;
        this.currentUsername = currentUsername;

        initEventHandlers();
        loadGroups();
    }

    private void initEventHandlers() {
        // Chọn một nhóm trong danh sách -> mở khung chat nhóm
        listView.setGroupSelectListener(this::openGroup);

        // Bấm nút tạo nhóm mới
        listView.setCreateListener(this::showCreateDialog);

        // Bấm nút xem thành viên ở header
        chatView.setMembersListener(this::showMembersDialog);

        // Gõ tin nhắn rồi gửi
        chatView.getInputPanel().setSendMessageListener(this::sendGroupMessage);

        // Gửi ảnh, video, tài liệu vào nhóm đang mở
        chatView.getInputPanel().setSendFileListener((file, fileType) -> {
            int groupId = chatView.getCurrentGroupId();
            if (groupId != 0) {
                FileClientManager.getInstance().sendGroupFile(writer, groupId, file, fileType);
            }
        });

        // Gửi đoạn ghi âm vào nhóm đang mở
        chatView.getInputPanel().setSendVoiceListener((wavBytes, durationSeconds) -> {
            int groupId = chatView.getCurrentGroupId();
            if (groupId != 0) {
                String fileName = "voice_" + System.currentTimeMillis() + ".wav";
                FileClientManager.getInstance().sendGroupBytes(writer, groupId, wavBytes, fileName, "AUDIO", durationSeconds);
            }
        });
    }

    // Gói này có phải của chat nhóm không
    public static boolean isGroupAction(String action) {
        return "CREATE_GROUP_RESPONSE".equalsIgnoreCase(action)
                || "GET_MY_GROUPS_RESPONSE".equalsIgnoreCase(action)
                || "GET_GROUP_MEMBERS_RESPONSE".equalsIgnoreCase(action)
                || "ADD_GROUP_MEMBER_RESPONSE".equalsIgnoreCase(action)
                || "REMOVE_GROUP_MEMBER_RESPONSE".equalsIgnoreCase(action)
                || "TRANSFER_LEADER_RESPONSE".equalsIgnoreCase(action)
                || "LEAVE_GROUP_RESPONSE".equalsIgnoreCase(action)
                || "DELETE_GROUP_RESPONSE".equalsIgnoreCase(action)
                || "GROUP_DELETED".equalsIgnoreCase(action)
                || "SEND_GROUP_MESSAGE_RESPONSE".equalsIgnoreCase(action)
                || "SEND_GROUP_FILE_RESPONSE".equalsIgnoreCase(action)
                || "GET_GROUP_HISTORY_RESPONSE".equalsIgnoreCase(action)
                || "RECEIVE_GROUP_MESSAGE".equalsIgnoreCase(action)
                || "GROUP_UPDATED".equalsIgnoreCase(action);
    }

    // Xử lý các gói chat nhóm, hàm này luôn được gọi trên luồng giao diện
    public void handleServerMessage(JsonObject msg) {
        String action = msg.get("action").getAsString();
        boolean success = msg.has("status") && "SUCCESS".equalsIgnoreCase(msg.get("status").getAsString());
        String message = msg.has("message") ? msg.get("message").getAsString() : "";
        JsonObject data = msg.has("data") && msg.get("data").isJsonObject() ? msg.getAsJsonObject("data") : null;

        SwingUtilities.invokeLater(() -> {
            switch (action) {
                case "GET_MY_GROUPS_RESPONSE":
                    if (success) onGroupsReceived(data);
                    break;
                case "GROUP_UPDATED":
                    loadGroups(); // nhóm thay đổi thì tải lại danh sách
                    break;
                case "GROUP_DELETED":
                    onGroupDeleted(data);
                    break;
                case "GET_GROUP_MEMBERS_RESPONSE":
                    if (success) onMembersReceived(data);
                    break;
                case "GET_GROUP_HISTORY_RESPONSE":
                    if (success) onHistoryReceived(data);
                    break;
                case "SEND_GROUP_MESSAGE_RESPONSE":
                case "SEND_GROUP_FILE_RESPONSE":
                    if (success) onMessageArrived(data, false);
                    else showError(message);
                    break;
                case "RECEIVE_GROUP_MESSAGE":
                    onMessageArrived(data, true);
                    break;
                case "CREATE_GROUP_RESPONSE":
                    if (success) pendingOpenGroupId = data.get("groupId").getAsInt();
                    else showError(message);
                    break;
                default:
                    // Thêm / xóa / chuyển quyền / rời nhóm: thành công thì GROUP_UPDATED sẽ làm mới, thất bại thì báo lỗi
                    if (!success) showError(message);
                    break;
            }
        });
    }

    // ---- Gửi yêu cầu ----
    // Đóng gói rồi gửi một yêu cầu JSON lên Server
    private void sendRequest(String action, JsonObject data) {
        JsonObject req = new JsonObject();
        req.addProperty("action", action);
        req.add("data", data);
        writer.println(gson.toJson(req));
    }

    // Tải danh sách nhóm mà mình tham gia
    public void loadGroups() {
        sendRequest("GET_MY_GROUPS", new JsonObject());
    }

    // Mở khung chat của nhóm và tải lịch sử tin nhắn
    private void openGroup(GroupItem group) {
        if (membersDialog != null) {
            membersDialog.setVisible(false);
        }
        chatView.showGroup(group);
        chatView.getMessageListPanel().clearMessages();

        JsonObject data = new JsonObject();
        data.addProperty("groupId", group.getId());
        data.addProperty("limit", 50);
        sendRequest("GET_GROUP_HISTORY", data);
    }

    // Gửi tin nhắn vào nhóm đang mở
    private void sendGroupMessage(String content) {
        int groupId = chatView.getCurrentGroupId();
        if (groupId == 0 || content.trim().isEmpty()) {
            return;
        }
        JsonObject data = new JsonObject();
        data.addProperty("groupId", groupId);
        data.addProperty("content", content);
        sendRequest("SEND_GROUP_MESSAGE", data);
    }

    // Yêu cầu Server trả về danh sách thành viên của nhóm
    private void requestMembers(int groupId) {
        JsonObject data = new JsonObject();
        data.addProperty("groupId", groupId);
        sendRequest("GET_GROUP_MEMBERS", data);
    }

    // ---- Hộp thoại ----
    // Mở hộp thoại chọn tên nhóm + thành viên rồi gửi yêu cầu tạo nhóm
    private void showCreateDialog() {
        CreateGroupDialog dialog = new CreateGroupDialog(parent, userListPanel.getUsers(), (name, members) -> {
            JsonObject data = new JsonObject();
            data.addProperty("name", name);
            JsonArray arr = new JsonArray();
            for (String m : members) {
                arr.add(m);
            }
            data.add("members", arr);
            sendRequest("CREATE_GROUP", data);
        });
        dialog.setVisible(true);
    }

    // Mở hộp thoại danh sách thành viên của nhóm đang chat
    private void showMembersDialog() {
        if (chatView.getCurrentGroup() == null) {
            return;
        }
        if (membersDialog == null) {
            membersDialog = new GroupMembersDialog(parent, currentUsername, new GroupMembersDialog.Actions() {
                @Override
                public void onAddMember() {
                    addMember();
                }

                @Override
                public void onRemoveMember(String username) {
                    removeMember(username);
                }

                @Override
                public void onTransferLeader(String username) {
                    transferLeader(username);
                }

                @Override
                public void onLeaveGroup() {
                    leaveGroup();
                }

                @Override
                public void onDeleteGroup() {
                    deleteGroup();
                }
            });
        }
        requestMembers(chatView.getCurrentGroupId());
        membersDialog.setVisible(true);
    }

    // Trưởng nhóm chọn một người dùng chưa có trong nhóm để thêm vào
    private void addMember() {
        List<UserListPanel.UserItem> candidates = new ArrayList<>();
        for (UserListPanel.UserItem user : userListPanel.getUsers()) {
            if (!isInGroup(user.getUsername())) {
                candidates.add(user);
            }
        }
        if (candidates.isEmpty()) {
            JOptionPane.showMessageDialog(membersDialog, "Không còn người dùng nào để thêm!");
            return;
        }

        String[] labels = new String[candidates.size()];
        for (int i = 0; i < labels.length; i++) {
            labels[i] = candidates.get(i).getFullName() + " (@" + candidates.get(i).getUsername() + ")";
        }
        Object chosen = JOptionPane.showInputDialog(membersDialog, "Chọn người cần thêm:", "Thêm thành viên",
                JOptionPane.QUESTION_MESSAGE, null, labels, labels[0]);
        if (chosen == null) {
            return;
        }

        for (int i = 0; i < labels.length; i++) {
            if (labels[i].equals(chosen)) {
                sendMemberRequest("ADD_GROUP_MEMBER", candidates.get(i).getUsername());
            }
        }
    }

    // Trưởng nhóm xóa một thành viên (có hỏi xác nhận)
    private void removeMember(String username) {
        int ok = JOptionPane.showConfirmDialog(membersDialog, "Xóa @" + username + " khỏi nhóm?", "Xác nhận",
                JOptionPane.YES_NO_OPTION);
        if (ok == JOptionPane.YES_OPTION) {
            sendMemberRequest("REMOVE_GROUP_MEMBER", username);
        }
    }

    // Trưởng nhóm chuyển quyền cho thành viên khác (có hỏi xác nhận)
    private void transferLeader(String username) {
        int ok = JOptionPane.showConfirmDialog(membersDialog,
                "Chuyển quyền trưởng nhóm cho @" + username + "?", "Xác nhận", JOptionPane.YES_NO_OPTION);
        if (ok == JOptionPane.YES_OPTION) {
            sendMemberRequest("TRANSFER_LEADER", username);
        }
    }

    // Rời khỏi nhóm đang mở (có hỏi xác nhận)
    private void leaveGroup() {
        int ok = JOptionPane.showConfirmDialog(membersDialog, "Bạn chắc chắn muốn rời nhóm?", "Xác nhận",
                JOptionPane.YES_NO_OPTION);
        if (ok == JOptionPane.YES_OPTION) {
            JsonObject data = new JsonObject();
            data.addProperty("groupId", chatView.getCurrentGroupId());
            sendRequest("LEAVE_GROUP", data);
        }
    }

    // Trưởng nhóm xóa hẳn nhóm đang mở (có hỏi xác nhận, vì không thể hoàn tác)
    private void deleteGroup() {
        GroupItem group = chatView.getCurrentGroup();
        if (group == null) {
            return;
        }
        int ok = JOptionPane.showConfirmDialog(membersDialog,
                "Xóa nhóm \"" + group.getName() + "\"?\nToàn bộ thành viên và tin nhắn của nhóm sẽ bị xóa, không thể hoàn tác.",
                "Xác nhận xóa nhóm", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (ok == JOptionPane.YES_OPTION) {
            JsonObject data = new JsonObject();
            data.addProperty("groupId", group.getId());
            sendRequest("DELETE_GROUP", data);
        }
    }

    // Gửi yêu cầu có kèm groupId và username (thêm / xóa / chuyển quyền)
    private void sendMemberRequest(String action, String username) {
        JsonObject data = new JsonObject();
        data.addProperty("groupId", chatView.getCurrentGroupId());
        data.addProperty("username", username);
        sendRequest(action, data);
    }

    // Kiểm tra một user đã nằm trong nhóm (theo danh sách thành viên đang hiển thị) chưa
    private boolean isInGroup(String username) {
        for (MemberItem m : membersDialog.getMembers()) {
            if (m.getUsername().equalsIgnoreCase(username)) {
                return true;
            }
        }
        return false;
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(parent, message, "Lỗi", JOptionPane.ERROR_MESSAGE);
    }

    // ---- Xử lý gói Server trả về ----
    // Nhận danh sách nhóm: cập nhật danh sách, header; nếu mình không còn trong nhóm đang mở thì đóng lại
    private void onGroupsReceived(JsonObject data) {
        List<GroupItem> groups = new ArrayList<>();
        for (JsonElement elem : data.getAsJsonArray("groups")) {
            groups.add(GroupItem.fromJson(elem.getAsJsonObject()));
        }

        // Vừa tạo nhóm xong -> mở luôn nhóm đó
        GroupItem created = findGroup(groups, pendingOpenGroupId);
        if (created != null) {
            pendingOpenGroupId = 0;
            listView.setGroups(groups, created.getId());
            openGroup(created);
            return;
        }

        GroupItem current = chatView.getCurrentGroup();
        int currentId = chatView.getCurrentGroupId();
        GroupItem updated = findGroup(groups, currentId);
        listView.setGroups(groups, currentId);

        if (current != null && updated == null) {
            // Bị xóa khỏi nhóm hoặc đã rời nhóm
            chatView.showPlaceholder();
            if (membersDialog != null) {
                membersDialog.setVisible(false);
            }
            JOptionPane.showMessageDialog(parent, "Bạn không còn ở trong nhóm \"" + current.getName() + "\"");
        } else if (updated != null) {
            chatView.updateGroup(updated);
            if (membersDialog != null && membersDialog.isVisible()) {
                requestMembers(updated.getId());
            }
        }
    }

    // Nhóm bị xóa: đang mở nhóm đó thì đóng lại và báo cho người dùng, sau đó tải lại danh sách nhóm
    private void onGroupDeleted(JsonObject data) {
        int groupId = data.get("groupId").getAsInt();
        String groupName = data.get("groupName").getAsString();
        String deletedBy = data.get("deletedBy").getAsString();

        if (groupId == chatView.getCurrentGroupId()) {
            // Đóng trước để onGroupsReceived không báo thêm "Bạn không còn ở trong nhóm"
            chatView.showPlaceholder();
            if (membersDialog != null) {
                membersDialog.setVisible(false);
            }
        }
        String text = deletedBy.equalsIgnoreCase(currentUsername)
                ? "Đã xóa nhóm \"" + groupName + "\"."
                : "Nhóm \"" + groupName + "\" đã bị trưởng nhóm @" + deletedBy + " xóa.";
        JOptionPane.showMessageDialog(parent, text);
        loadGroups();
    }

    // Nhận danh sách thành viên -> đổ vào hộp thoại
    private void onMembersReceived(JsonObject data) {
        if (membersDialog == null || data.get("groupId").getAsInt() != chatView.getCurrentGroupId()) {
            return;
        }
        List<MemberItem> members = new ArrayList<>();
        for (JsonElement elem : data.getAsJsonArray("members")) {
            members.add(MemberItem.fromJson(elem.getAsJsonObject()));
        }
        membersDialog.setMembers(chatView.getCurrentGroup().getName(), members);
    }

    // Nhận lịch sử tin nhắn của nhóm đang mở
    private void onHistoryReceived(JsonObject data) {
        if (data.get("groupId").getAsInt() != chatView.getCurrentGroupId()) {
            return;
        }
        List<ChatMessage> list = new ArrayList<>();
        for (JsonElement elem : data.getAsJsonArray("messages")) {
            list.add(ChatMessage.fromJson(elem.getAsJsonObject(), currentUsername));
        }
        chatView.getMessageListPanel().setMessages(list);
    }

    // Có tin nhắn mới (của mình hoặc của người khác): đang mở nhóm đó thì hiện bong bóng, không thì tăng số tin chưa xem
    private void onMessageArrived(JsonObject data, boolean fromOthers) {
        int groupId = data.get("groupId").getAsInt();
        if (groupId == chatView.getCurrentGroupId()) {
            chatView.getMessageListPanel().addMessage(ChatMessage.fromJson(data, currentUsername));
        } else if (fromOthers) {
            listView.addUnread(groupId);
        }
    }

    // Tìm nhóm theo id trong danh sách
    private GroupItem findGroup(List<GroupItem> groups, int groupId) {
        for (GroupItem g : groups) {
            if (g.getId() == groupId) {
                return g;
            }
        }
        return null;
    }
}
