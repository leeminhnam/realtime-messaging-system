package group_chat.handler;

import account.UserDAO;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import group_chat.dao.GroupDAO;
import group_chat.dao.GroupMessageDAO;
import group_chat.model.ChatGroup;
import group_chat.model.GroupMember;
import group_chat.model.GroupMessage;
import private_chat.handler.PrivateChatHandler;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class GroupChatHandler {
    private static final Gson gson = new Gson();

    // Các action của chat nhóm
    public static boolean isGroupChatAction(String action) {
        return "CREATE_GROUP".equalsIgnoreCase(action)
                || "GET_MY_GROUPS".equalsIgnoreCase(action)
                || "GET_GROUP_MEMBERS".equalsIgnoreCase(action)
                || "ADD_GROUP_MEMBER".equalsIgnoreCase(action)
                || "REMOVE_GROUP_MEMBER".equalsIgnoreCase(action)
                || "TRANSFER_LEADER".equalsIgnoreCase(action)
                || "LEAVE_GROUP".equalsIgnoreCase(action)
                || "DELETE_GROUP".equalsIgnoreCase(action)
                || "SEND_GROUP_MESSAGE".equalsIgnoreCase(action)
                || "GET_GROUP_HISTORY".equalsIgnoreCase(action);
    }

    // Mọi action chat nhóm đều cần đăng nhập trước
    public static void handleGroupChatAction(JsonObject req, PrintWriter writer, String currentUsername,
            Map<String, PrintWriter> onlineClients) {

        if (currentUsername == null || currentUsername.trim().isEmpty()) {
            sendResponse(writer, "ERROR", "FAILED", "Bạn cần đăng nhập để dùng chat nhóm!", null);
            return;
        }

        String action = req.has("action") ? req.get("action").getAsString() : "";
        JsonObject data = req.has("data") && !req.get("data").isJsonNull() ? req.getAsJsonObject("data")
                : new JsonObject();

        switch (action) {
            case "CREATE_GROUP":
                handleCreateGroup(data, writer, currentUsername, onlineClients);
                break;
            case "GET_MY_GROUPS":
                handleGetMyGroups(writer, currentUsername);
                break;
            case "GET_GROUP_MEMBERS":
                handleGetMembers(data, writer, currentUsername);
                break;
            case "ADD_GROUP_MEMBER":
                handleAddMember(data, writer, currentUsername, onlineClients);
                break;
            case "REMOVE_GROUP_MEMBER":
                handleRemoveMember(data, writer, currentUsername, onlineClients);
                break;
            case "TRANSFER_LEADER":
                handleTransferLeader(data, writer, currentUsername, onlineClients);
                break;
            case "LEAVE_GROUP":
                handleLeaveGroup(data, writer, currentUsername, onlineClients);
                break;
            case "DELETE_GROUP":
                handleDeleteGroup(data, writer, currentUsername, onlineClients);
                break;
            case "SEND_GROUP_MESSAGE":
                handleSendMessage(data, writer, currentUsername, onlineClients);
                break;
            case "GET_GROUP_HISTORY":
                handleGetHistory(data, writer, currentUsername);
                break;
            default:
                sendResponse(writer, "ERROR", "FAILED", "Hành động chat nhóm không hợp lệ!", null);
                break;
        }
    }

    // Tạo nhóm mới
    private static void handleCreateGroup(JsonObject data, PrintWriter writer, String currentUsername,
            Map<String, PrintWriter> onlineClients) {

        String name = data.has("name") ? data.get("name").getAsString().trim() : "";
        if (name.isEmpty() || name.length() > 50) {
            sendResponse(writer, "CREATE_GROUP_RESPONSE", "FAILED", "Tên nhóm phải từ 1 đến 50 ký tự!", null);
            return;
        }

        List<String> members = new ArrayList<>();
        if (data.has("members")) {
            for (JsonElement elem : data.getAsJsonArray("members")) {
                String username = elem.getAsString().trim();
                if (!username.equalsIgnoreCase(currentUsername)
                        && !containsIgnoreCase(members, username)
                        && UserDAO.getUserByUsername(username) != null) {
                    members.add(username);
                }
            }
        }
        if (members.size() < 2) {
            sendResponse(writer, "CREATE_GROUP_RESPONSE", "FAILED", "Nhóm cần tối thiểu 3 người, hãy chọn ít nhất 2 thành viên!", null);
            return;
        }

        int groupId = GroupDAO.createGroup(name, currentUsername, members);
        if (groupId < 0) {
            sendResponse(writer, "CREATE_GROUP_RESPONSE", "FAILED", "Không thể tạo nhóm!", null);
            return;
        }

        JsonObject resData = new JsonObject();
        resData.addProperty("groupId", groupId);
        sendResponse(writer, "CREATE_GROUP_RESPONSE", "SUCCESS", "Tạo nhóm thành công!", resData);

        System.out.println("[GROUP] @" + currentUsername + " tạo nhóm '" + name + "' (id=" + groupId + ")");
        notifyGroupUpdated(groupId, getMemberUsernames(groupId), onlineClients);
    }

    // Các nhóm mà người dùng đang tham gia
    private static void handleGetMyGroups(PrintWriter writer, String currentUsername) {
        List<ChatGroup> groups = GroupDAO.getGroupsOfUser(currentUsername);

        JsonArray arr = new JsonArray();
        for (ChatGroup g : groups) {
            JsonObject obj = new JsonObject();
            obj.addProperty("id", g.getId());
            obj.addProperty("name", g.getName());
            obj.addProperty("leader", g.getLeaderUsername());
            obj.addProperty("memberCount", g.getMemberCount());
            arr.add(obj);
        }

        JsonObject resData = new JsonObject();
        resData.add("groups", arr);
        sendResponse(writer, "GET_MY_GROUPS_RESPONSE", "SUCCESS", "Tải danh sách nhóm thành công!", resData);
    }

    private static void handleGetMembers(JsonObject data, PrintWriter writer, String currentUsername) {
        int groupId = data.has("groupId") ? data.get("groupId").getAsInt() : 0;
        if (GroupDAO.getRole(groupId, currentUsername) == null) {
            sendResponse(writer, "GET_GROUP_MEMBERS_RESPONSE", "FAILED", "Bạn không thuộc nhóm này!", null);
            return;
        }

        JsonObject resData = new JsonObject();
        resData.addProperty("groupId", groupId);
        resData.add("members", gson.toJsonTree(GroupDAO.getMembers(groupId)));
        sendResponse(writer, "GET_GROUP_MEMBERS_RESPONSE", "SUCCESS", "Tải thành viên thành công!", resData);
    }

    private static void handleAddMember(JsonObject data, PrintWriter writer, String currentUsername,
            Map<String, PrintWriter> onlineClients) {

        int groupId = data.has("groupId") ? data.get("groupId").getAsInt() : 0;
        String target = data.has("username") ? data.get("username").getAsString().trim() : "";

        if (!isLeader(groupId, currentUsername)) {
            sendResponse(writer, "ADD_GROUP_MEMBER_RESPONSE", "FAILED", "Chỉ trưởng nhóm mới được thêm thành viên!", null);
            return;
        }
        if (target.isEmpty() || UserDAO.getUserByUsername(target) == null) {
            sendResponse(writer, "ADD_GROUP_MEMBER_RESPONSE", "FAILED", "Người dùng không tồn tại!", null);
            return;
        }
        if (GroupDAO.getRole(groupId, target) != null) {
            sendResponse(writer, "ADD_GROUP_MEMBER_RESPONSE", "FAILED", "Người này đã ở trong nhóm!", null);
            return;
        }
        if (!GroupDAO.addMember(groupId, target, GroupDAO.MEMBER)) {
            sendResponse(writer, "ADD_GROUP_MEMBER_RESPONSE", "FAILED", "Không thể thêm thành viên!", null);
            return;
        }

        sendResponse(writer, "ADD_GROUP_MEMBER_RESPONSE", "SUCCESS", "Đã thêm @" + target + " vào nhóm!", null);
        notifyGroupUpdated(groupId, getMemberUsernames(groupId), onlineClients);
    }

    private static void handleRemoveMember(JsonObject data, PrintWriter writer, String currentUsername,
            Map<String, PrintWriter> onlineClients) {

        int groupId = data.has("groupId") ? data.get("groupId").getAsInt() : 0;
        String target = data.has("username") ? data.get("username").getAsString().trim() : "";

        if (!isLeader(groupId, currentUsername)) {
            sendResponse(writer, "REMOVE_GROUP_MEMBER_RESPONSE", "FAILED", "Chỉ trưởng nhóm mới được xóa thành viên!", null);
            return;
        }
        if (target.equalsIgnoreCase(currentUsername)) {
            sendResponse(writer, "REMOVE_GROUP_MEMBER_RESPONSE", "FAILED", "Không thể tự xóa mình, hãy dùng chức năng Rời nhóm!", null);
            return;
        }
        if (GroupDAO.getRole(groupId, target) == null) {
            sendResponse(writer, "REMOVE_GROUP_MEMBER_RESPONSE", "FAILED", "Người này không ở trong nhóm!", null);
            return;
        }

        List<String> toNotify = getMemberUsernames(groupId);
        GroupDAO.removeMember(groupId, target);

        sendResponse(writer, "REMOVE_GROUP_MEMBER_RESPONSE", "SUCCESS", "Đã xóa @" + target + " khỏi nhóm!", null);
        notifyGroupUpdated(groupId, toNotify, onlineClients);
    }

    // Chuyển quyền trưởng nhóm
    private static void handleTransferLeader(JsonObject data, PrintWriter writer, String currentUsername,
            Map<String, PrintWriter> onlineClients) {

        int groupId = data.has("groupId") ? data.get("groupId").getAsInt() : 0;
        String target = data.has("username") ? data.get("username").getAsString().trim() : "";

        if (!isLeader(groupId, currentUsername)) {
            sendResponse(writer, "TRANSFER_LEADER_RESPONSE", "FAILED", "Chỉ trưởng nhóm mới được chuyển quyền!", null);
            return;
        }
        if (target.equalsIgnoreCase(currentUsername) || GroupDAO.getRole(groupId, target) == null) {
            sendResponse(writer, "TRANSFER_LEADER_RESPONSE", "FAILED", "Hãy chọn một thành viên khác trong nhóm!", null);
            return;
        }

        GroupDAO.transferLeader(groupId, currentUsername, target);

        sendResponse(writer, "TRANSFER_LEADER_RESPONSE", "SUCCESS", "Đã chuyển quyền trưởng nhóm cho @" + target + "!", null);
        notifyGroupUpdated(groupId, getMemberUsernames(groupId), onlineClients);
    }

    // Rời nhóm
    private static void handleLeaveGroup(JsonObject data, PrintWriter writer, String currentUsername,
            Map<String, PrintWriter> onlineClients) {

        int groupId = data.has("groupId") ? data.get("groupId").getAsInt() : 0;
        String role = GroupDAO.getRole(groupId, currentUsername);
        if (role == null) {
            sendResponse(writer, "LEAVE_GROUP_RESPONSE", "FAILED", "Bạn không thuộc nhóm này!", null);
            return;
        }

        List<String> toNotify = getMemberUsernames(groupId);

        if (GroupDAO.LEADER.equals(role)) {
            if (toNotify.size() > 1) {
                sendResponse(writer, "LEAVE_GROUP_RESPONSE", "FAILED", "Trưởng nhóm cần chuyển quyền cho người khác trước khi rời nhóm!", null);
                return;
            }
            GroupDAO.deleteGroup(groupId);
        } else {
            GroupDAO.removeMember(groupId, currentUsername);
        }

        sendResponse(writer, "LEAVE_GROUP_RESPONSE", "SUCCESS", "Bạn đã rời nhóm!", null);
        notifyGroupUpdated(groupId, toNotify, onlineClients);
    }

    // Chỉ trưởng nhóm được xóa nhóm
    private static void handleDeleteGroup(JsonObject data, PrintWriter writer, String currentUsername,
            Map<String, PrintWriter> onlineClients) {

        int groupId = data.has("groupId") ? data.get("groupId").getAsInt() : 0;
        if (!isLeader(groupId, currentUsername)) {
            sendResponse(writer, "DELETE_GROUP_RESPONSE", "FAILED", "Chỉ trưởng nhóm mới được xóa nhóm!", null);
            return;
        }

        ChatGroup group = GroupDAO.getGroupById(groupId);
        String groupName = group != null ? group.getName() : "";
        List<String> toNotify = getMemberUsernames(groupId);
        GroupDAO.deleteGroup(groupId);

        JsonObject resData = new JsonObject();
        resData.addProperty("groupId", groupId);
        sendResponse(writer, "DELETE_GROUP_RESPONSE", "SUCCESS", "Đã xóa nhóm \"" + groupName + "\"!", resData);

        System.out.println("[GROUP] @" + currentUsername + " xóa nhóm '" + groupName + "' (id=" + groupId + ")");
        notifyGroupDeleted(groupId, groupName, currentUsername, toNotify, onlineClients);
    }

    // Lưu tin trước, phản hồi người gửi rồi mới chuyển cho các thành viên đang online
    private static void handleSendMessage(JsonObject data, PrintWriter writer, String currentUsername,
            Map<String, PrintWriter> onlineClients) {

        int groupId = data.has("groupId") ? data.get("groupId").getAsInt() : 0;
        String content = data.has("content") ? data.get("content").getAsString().trim() : "";

        if (GroupDAO.getRole(groupId, currentUsername) == null) {
            sendResponse(writer, "SEND_GROUP_MESSAGE_RESPONSE", "FAILED", "Bạn không thuộc nhóm này!", null);
            return;
        }
        if (content.isEmpty()) {
            sendResponse(writer, "SEND_GROUP_MESSAGE_RESPONSE", "FAILED", "Nội dung tin nhắn không được để trống!", null);
            return;
        }

        GroupMessage saved = GroupMessageDAO.saveMessage(groupId, currentUsername, content);
        if (saved == null) {
            sendResponse(writer, "SEND_GROUP_MESSAGE_RESPONSE", "FAILED", "Lỗi lưu tin nhắn vào CSDL!", null);
            return;
        }

        JsonObject msgData = messageToJson(saved);
        sendResponse(writer, "SEND_GROUP_MESSAGE_RESPONSE", "SUCCESS", "Gửi tin nhắn nhóm thành công!", msgData);
        forwardToMembers(saved.getGroupId(), currentUsername, msgData, onlineClients);
    }

    // Đẩy RECEIVE_GROUP_MESSAGE tới các thành viên online
    public static void forwardToMembers(int groupId, String senderUsername, JsonObject msgData,
            Map<String, PrintWriter> onlineClients) {
        JsonObject receivePacket = new JsonObject();
        receivePacket.addProperty("action", "RECEIVE_GROUP_MESSAGE");
        receivePacket.addProperty("status", "SUCCESS");
        receivePacket.add("data", msgData);
        String packet = gson.toJson(receivePacket);

        for (String member : getMemberUsernames(groupId)) {
            if (!member.equalsIgnoreCase(senderUsername)) {
                PrintWriter memberWriter = findWriter(onlineClients, member);
                if (memberWriter != null) {
                    memberWriter.println(packet);
                }
            }
        }
    }

    // Lịch sử nhóm
    private static void handleGetHistory(JsonObject data, PrintWriter writer, String currentUsername) {
        int groupId = data.has("groupId") ? data.get("groupId").getAsInt() : 0;
        int limit = data.has("limit") ? data.get("limit").getAsInt() : 50;

        if (GroupDAO.getRole(groupId, currentUsername) == null) {
            sendResponse(writer, "GET_GROUP_HISTORY_RESPONSE", "FAILED", "Bạn không thuộc nhóm này!", null);
            return;
        }

        JsonArray arr = new JsonArray();
        for (GroupMessage m : GroupMessageDAO.getHistory(groupId, limit)) {
            arr.add(messageToJson(m));
        }

        JsonObject resData = new JsonObject();
        resData.addProperty("groupId", groupId);
        resData.add("messages", arr);
        sendResponse(writer, "GET_GROUP_HISTORY_RESPONSE", "SUCCESS", "Tải lịch sử nhóm thành công!", resData);
    }

    // Báo GROUP_UPDATED để Client tự tải lại danh sách nhóm
    private static void notifyGroupUpdated(int groupId, List<String> usernames, Map<String, PrintWriter> onlineClients) {
        JsonObject packet = new JsonObject();
        packet.addProperty("action", "GROUP_UPDATED");
        packet.addProperty("status", "SUCCESS");
        JsonObject data = new JsonObject();
        data.addProperty("groupId", groupId);
        packet.add("data", data);
        String json = gson.toJson(packet);

        for (String username : usernames) {
            PrintWriter w = findWriter(onlineClients, username);
            if (w != null) {
                w.println(json);
            }
        }
    }

    // Báo GROUP_DELETED để Client đóng khung chat nhóm đó và tải lại danh sách nhóm
    private static void notifyGroupDeleted(int groupId, String groupName, String deletedBy, List<String> usernames,
            Map<String, PrintWriter> onlineClients) {
        JsonObject packet = new JsonObject();
        packet.addProperty("action", "GROUP_DELETED");
        packet.addProperty("status", "SUCCESS");
        JsonObject data = new JsonObject();
        data.addProperty("groupId", groupId);
        data.addProperty("groupName", groupName);
        data.addProperty("deletedBy", deletedBy);
        packet.add("data", data);
        String json = gson.toJson(packet);

        for (String username : usernames) {
            PrintWriter w = findWriter(onlineClients, username);
            if (w != null) {
                w.println(json);
            }
        }
    }

    private static boolean isLeader(int groupId, String username) {
        return GroupDAO.LEADER.equals(GroupDAO.getRole(groupId, username));
    }

    private static List<String> getMemberUsernames(int groupId) {
        List<String> names = new ArrayList<>();
        for (GroupMember m : GroupDAO.getMembers(groupId)) {
            names.add(m.getUsername());
        }
        return names;
    }

    // Tìm theo username, không phân biệt hoa thường
    private static PrintWriter findWriter(Map<String, PrintWriter> onlineClients, String username) {
        for (Map.Entry<String, PrintWriter> entry : onlineClients.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(username)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private static boolean containsIgnoreCase(List<String> list, String value) {
        for (String s : list) {
            if (s.equalsIgnoreCase(value)) {
                return true;
            }
        }
        return false;
    }

    // Đổi GroupMessage thành JSON gửi về Client
    public static JsonObject messageToJson(GroupMessage m) {
        JsonObject obj = new JsonObject();
        obj.addProperty("id", m.getId());
        obj.addProperty("groupId", m.getGroupId());
        obj.addProperty("sender", m.getSenderUsername());
        obj.addProperty("senderFullName", m.getSenderFullName());
        obj.addProperty("content", m.getContent());
        obj.addProperty("sentAt", m.getSentAt());
        return obj;
    }

    private static void sendResponse(PrintWriter writer, String action, String status, String message, JsonObject data) {
        PrivateChatHandler.sendResponse(writer, action, status, message, data);
    }
}
