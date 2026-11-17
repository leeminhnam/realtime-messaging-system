package group_chat;

import com.google.gson.JsonObject;
import group_chat.dao.GroupDAO;
import group_chat.dao.GroupMessageDAO;
import group_chat.handler.GroupChatHandler;
import group_chat.model.ChatGroup;
import group_chat.model.GroupMember;
import group_chat.model.GroupMessage;
import testutil.TestSupport;
import testutil.TestSupport.Capture;

import java.io.PrintWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static testutil.TestSupport.check;
import static testutil.TestSupport.isStatus;
import static testutil.TestSupport.request;

/** Kiểm thử Module 3 (Group Chat): GroupDAO, GroupMessageDAO, GroupChatHandler. */
public class GroupChatModuleTest {

    public static void main(String[] args) throws Exception {
        TestSupport.initTempDb();
        run();
        System.exit(TestSupport.summary());
    }

    public static void run() {
        TestSupport.section("MODULE 3 - GROUP CHAT");
        TestSupport.registerUsers("gc_lead", "gc_m1", "gc_m2", "gc_m3", "gc_out");

        testGroupDao();
        testGroupMessageDao();
        testHandlerCreateGroup();
        testHandlerMembersManagement();
        testHandlerLeaderTransferAndLeave();
        testHandlerDeleteGroup();
        testHandlerMessaging();
        testIsGroupChatAction();
    }

    // ---------- GroupDAO ----------
    static void testGroupDao() {
        int id = GroupDAO.createGroup("Nhóm DAO", "gc_lead", List.of("gc_m1", "gc_m2"));
        check("group: tạo nhóm trả về id > 0", id > 0);
        check("group: người tạo là LEADER", GroupDAO.LEADER.equals(GroupDAO.getRole(id, "gc_lead")));
        check("group: các thành viên là MEMBER", GroupDAO.MEMBER.equals(GroupDAO.getRole(id, "gc_m1")));
        check("group: người ngoài nhóm -> role null", GroupDAO.getRole(id, "gc_out") == null);

        ChatGroup g = GroupDAO.getGroupById(id);
        check("group: getGroupById trả về tên, trưởng nhóm và số thành viên",
                g != null && "Nhóm DAO".equals(g.getName()) && "gc_lead".equals(g.getLeaderUsername())
                        && g.getMemberCount() == 3);
        check("group: getGroupById không tồn tại -> null", GroupDAO.getGroupById(999999) == null);

        List<GroupMember> members = GroupDAO.getMembers(id);
        check("group: getMembers xếp trưởng nhóm đầu tiên, kèm họ tên",
                members.size() == 3 && "gc_lead".equals(members.get(0).getUsername())
                        && "Name of gc_lead".equals(members.get(0).getFullName()));
        check("group: getGroupsOfUser trả về nhóm của thành viên", GroupDAO.getGroupsOfUser("gc_m1").size() == 1);
        check("group: getGroupsOfUser của người ngoài = rỗng", GroupDAO.getGroupsOfUser("gc_out").isEmpty());

        check("group: addMember người mới -> true", GroupDAO.addMember(id, "gc_out", GroupDAO.MEMBER));
        check("group: addMember trùng -> false", !GroupDAO.addMember(id, "gc_out", GroupDAO.MEMBER));
        check("group: removeMember -> true", GroupDAO.removeMember(id, "gc_out"));
        check("group: removeMember người không có -> false", !GroupDAO.removeMember(id, "gc_out"));

        check("group: transferLeader thành công", GroupDAO.transferLeader(id, "gc_lead", "gc_m1"));
        check("group: sau chuyển quyền vai trò đổi chỗ",
                GroupDAO.LEADER.equals(GroupDAO.getRole(id, "gc_m1"))
                        && GroupDAO.MEMBER.equals(GroupDAO.getRole(id, "gc_lead")));

        GroupMessageDAO.saveMessage(id, "gc_m1", "tin trước khi xóa");
        GroupDAO.deleteGroup(id);
        check("group: deleteGroup xóa nhóm, thành viên và tin nhắn",
                GroupDAO.getGroupById(id) == null && GroupDAO.getMembers(id).isEmpty()
                        && GroupMessageDAO.getHistory(id, 50).isEmpty());
    }

    // ---------- GroupMessageDAO ----------
    static void testGroupMessageDao() {
        int id = GroupDAO.createGroup("Nhóm tin nhắn", "gc_lead", List.of("gc_m1"));
        GroupMessage m = GroupMessageDAO.saveMessage(id, "gc_lead", "tin 1");
        check("group message: lưu thành công, có id/sentAt/họ tên người gửi",
                m != null && m.getId() > 0 && m.getSentAt() != null && "Name of gc_lead".equals(m.getSenderFullName()));

        GroupMessageDAO.saveMessage(id, "gc_m1", "tin 2");
        GroupMessageDAO.saveMessage(id, "gc_lead", "tin 3");
        List<GroupMessage> last2 = GroupMessageDAO.getHistory(id, 2);
        check("group message: limit=2 lấy 2 tin mới nhất theo thứ tự cũ -> mới",
                last2.size() == 2 && "tin 2".equals(last2.get(0).getContent()) && "tin 3".equals(last2.get(1).getContent()));

        GroupMessage ghost = GroupMessageDAO.saveMessage(id, "khong_ton_tai", "từ user lạ");
        check("group message: người gửi không có hồ sơ -> dùng username làm tên hiển thị",
                ghost != null && "khong_ton_tai".equals(ghost.getSenderFullName()));
        check("group message: getMessageById không tồn tại -> null", GroupMessageDAO.getMessageById(999999) == null);
    }

    // ---------- GroupChatHandler ----------
    static JsonObject call(String action, String dataJson, String user, Map<String, PrintWriter> online) {
        Capture out = new Capture();
        GroupChatHandler.handleGroupChatAction(request(action, dataJson), out.writer, user, online);
        return out.last();
    }

    static int createViaHandler(String name, String membersJson, String leader, Map<String, PrintWriter> online) {
        JsonObject res = call("CREATE_GROUP", "{\"name\":\"" + name + "\",\"members\":" + membersJson + "}", leader, online);
        return isStatus(res, "CREATE_GROUP_RESPONSE", "SUCCESS") ? res.getAsJsonObject("data").get("groupId").getAsInt() : -1;
    }

    static void testHandlerCreateGroup() {
        Map<String, PrintWriter> online = new HashMap<>();

        check("handler: chưa đăng nhập -> ERROR/FAILED",
                isStatus(call("GET_MY_GROUPS", null, null, online), "ERROR", "FAILED"));
        check("handler CREATE: tên rỗng -> FAILED",
                isStatus(call("CREATE_GROUP", "{\"name\":\"  \",\"members\":[\"gc_m1\"]}", "gc_lead", online),
                        "CREATE_GROUP_RESPONSE", "FAILED"));
        check("handler CREATE: tên dài hơn 50 ký tự -> FAILED",
                isStatus(call("CREATE_GROUP", "{\"name\":\"" + "a".repeat(51) + "\",\"members\":[\"gc_m1\"]}", "gc_lead", online),
                        "CREATE_GROUP_RESPONSE", "FAILED"));
        check("handler CREATE: danh sách thành viên chỉ gồm chính mình/user không tồn tại -> FAILED",
                isStatus(call("CREATE_GROUP", "{\"name\":\"X\",\"members\":[\"gc_lead\",\"khong_co\"]}", "gc_lead", online),
                        "CREATE_GROUP_RESPONSE", "FAILED"));
        check("handler CREATE: chỉ 2 người (trưởng nhóm + 1 thành viên) -> FAILED",
                isStatus(call("CREATE_GROUP", "{\"name\":\"X\",\"members\":[\"gc_m1\"]}", "gc_lead", online),
                        "CREATE_GROUP_RESPONSE", "FAILED"));
        check("handler CREATE: sau khi lọc trùng/chính mình chỉ còn 1 thành viên -> FAILED",
                isStatus(call("CREATE_GROUP", "{\"name\":\"X\",\"members\":[\"gc_m1\",\"GC_M1\",\"gc_lead\"]}", "gc_lead", online),
                        "CREATE_GROUP_RESPONSE", "FAILED"));

        Capture m1 = new Capture();
        Capture lead = new Capture();
        online.put("gc_m1", m1.writer);
        online.put("gc_lead", lead.writer);

        // Trùng (không phân biệt hoa/thường), chính mình và user lạ đều bị lọc
        int gid = createViaHandler("Nhóm handler", "[\"gc_m1\",\"GC_M1\",\"gc_m2\",\"gc_lead\",\"khong_co\"]", "gc_lead", online);
        check("handler CREATE: thành công, lọc trùng/chính mình/user lạ -> nhóm 3 người",
                gid > 0 && GroupDAO.getMembers(gid).size() == 3);
        check("handler CREATE: thành viên online nhận GROUP_UPDATED",
                m1.find("GROUP_UPDATED") != null
                        && m1.find("GROUP_UPDATED").getAsJsonObject("data").get("groupId").getAsInt() == gid);

        JsonObject mine = call("GET_MY_GROUPS", null, "gc_m1", online);
        check("handler GET_MY_GROUPS: trả về nhóm kèm tên, trưởng nhóm, số thành viên",
                isStatus(mine, "GET_MY_GROUPS_RESPONSE", "SUCCESS")
                        && mine.getAsJsonObject("data").getAsJsonArray("groups").size() >= 1
                        && "gc_lead".equals(mine.getAsJsonObject("data").getAsJsonArray("groups").get(0)
                                .getAsJsonObject().get("leader").getAsString()));
    }

    static void testHandlerMembersManagement() {
        Map<String, PrintWriter> online = new HashMap<>();
        int gid = createViaHandler("Nhóm quản lý", "[\"gc_m1\",\"gc_m3\"]", "gc_lead", online);

        check("handler MEMBERS: thành viên xem được danh sách",
                isStatus(call("GET_GROUP_MEMBERS", "{\"groupId\":" + gid + "}", "gc_m1", online),
                        "GET_GROUP_MEMBERS_RESPONSE", "SUCCESS"));
        check("handler MEMBERS: người ngoài nhóm không xem được",
                isStatus(call("GET_GROUP_MEMBERS", "{\"groupId\":" + gid + "}", "gc_out", online),
                        "GET_GROUP_MEMBERS_RESPONSE", "FAILED"));

        check("handler ADD: thành viên thường không được thêm người",
                isStatus(call("ADD_GROUP_MEMBER", "{\"groupId\":" + gid + ",\"username\":\"gc_m2\"}", "gc_m1", online),
                        "ADD_GROUP_MEMBER_RESPONSE", "FAILED"));
        check("handler ADD: thêm user không tồn tại -> FAILED",
                isStatus(call("ADD_GROUP_MEMBER", "{\"groupId\":" + gid + ",\"username\":\"khong_co\"}", "gc_lead", online),
                        "ADD_GROUP_MEMBER_RESPONSE", "FAILED"));
        check("handler ADD: trưởng nhóm thêm thành viên -> SUCCESS",
                isStatus(call("ADD_GROUP_MEMBER", "{\"groupId\":" + gid + ",\"username\":\"gc_m2\"}", "gc_lead", online),
                        "ADD_GROUP_MEMBER_RESPONSE", "SUCCESS") && GroupDAO.getRole(gid, "gc_m2") != null);
        check("handler ADD: thêm người đã trong nhóm -> FAILED",
                isStatus(call("ADD_GROUP_MEMBER", "{\"groupId\":" + gid + ",\"username\":\"gc_m2\"}", "gc_lead", online),
                        "ADD_GROUP_MEMBER_RESPONSE", "FAILED"));

        check("handler REMOVE: thành viên thường không được xóa người",
                isStatus(call("REMOVE_GROUP_MEMBER", "{\"groupId\":" + gid + ",\"username\":\"gc_m2\"}", "gc_m1", online),
                        "REMOVE_GROUP_MEMBER_RESPONSE", "FAILED"));
        check("handler REMOVE: trưởng nhóm không tự xóa chính mình",
                isStatus(call("REMOVE_GROUP_MEMBER", "{\"groupId\":" + gid + ",\"username\":\"gc_lead\"}", "gc_lead", online),
                        "REMOVE_GROUP_MEMBER_RESPONSE", "FAILED"));
        check("handler REMOVE: xóa người không ở trong nhóm -> FAILED",
                isStatus(call("REMOVE_GROUP_MEMBER", "{\"groupId\":" + gid + ",\"username\":\"gc_out\"}", "gc_lead", online),
                        "REMOVE_GROUP_MEMBER_RESPONSE", "FAILED"));

        Capture removed = new Capture();
        online.put("gc_m2", removed.writer);
        check("handler REMOVE: trưởng nhóm xóa thành viên -> SUCCESS",
                isStatus(call("REMOVE_GROUP_MEMBER", "{\"groupId\":" + gid + ",\"username\":\"gc_m2\"}", "gc_lead", online),
                        "REMOVE_GROUP_MEMBER_RESPONSE", "SUCCESS") && GroupDAO.getRole(gid, "gc_m2") == null);
        check("handler REMOVE: người bị xóa vẫn nhận GROUP_UPDATED để tải lại danh sách",
                removed.find("GROUP_UPDATED") != null);
    }

    static void testHandlerLeaderTransferAndLeave() {
        Map<String, PrintWriter> online = new HashMap<>();
        int gid = createViaHandler("Nhóm chuyển quyền", "[\"gc_m1\",\"gc_m2\"]", "gc_lead", online);

        check("handler TRANSFER: thành viên thường không được chuyển quyền",
                isStatus(call("TRANSFER_LEADER", "{\"groupId\":" + gid + ",\"username\":\"gc_m2\"}", "gc_m1", online),
                        "TRANSFER_LEADER_RESPONSE", "FAILED"));
        check("handler TRANSFER: chuyển cho chính mình -> FAILED",
                isStatus(call("TRANSFER_LEADER", "{\"groupId\":" + gid + ",\"username\":\"gc_lead\"}", "gc_lead", online),
                        "TRANSFER_LEADER_RESPONSE", "FAILED"));
        check("handler TRANSFER: chuyển cho người ngoài nhóm -> FAILED",
                isStatus(call("TRANSFER_LEADER", "{\"groupId\":" + gid + ",\"username\":\"gc_out\"}", "gc_lead", online),
                        "TRANSFER_LEADER_RESPONSE", "FAILED"));

        check("handler LEAVE: trưởng nhóm còn người khác phải chuyển quyền trước",
                isStatus(call("LEAVE_GROUP", "{\"groupId\":" + gid + "}", "gc_lead", online), "LEAVE_GROUP_RESPONSE", "FAILED")
                        && GroupDAO.getRole(gid, "gc_lead") != null);

        check("handler TRANSFER: trưởng nhóm chuyển quyền -> SUCCESS",
                isStatus(call("TRANSFER_LEADER", "{\"groupId\":" + gid + ",\"username\":\"gc_m1\"}", "gc_lead", online),
                        "TRANSFER_LEADER_RESPONSE", "SUCCESS")
                        && GroupDAO.LEADER.equals(GroupDAO.getRole(gid, "gc_m1")));

        check("handler LEAVE: người ngoài nhóm rời nhóm -> FAILED",
                isStatus(call("LEAVE_GROUP", "{\"groupId\":" + gid + "}", "gc_out", online), "LEAVE_GROUP_RESPONSE", "FAILED"));
        check("handler LEAVE: thành viên thường rời nhóm -> SUCCESS",
                isStatus(call("LEAVE_GROUP", "{\"groupId\":" + gid + "}", "gc_m2", online), "LEAVE_GROUP_RESPONSE", "SUCCESS")
                        && GroupDAO.getRole(gid, "gc_m2") == null);
        check("handler LEAVE: cựu trưởng nhóm (nay là thành viên) rời nhóm -> SUCCESS",
                isStatus(call("LEAVE_GROUP", "{\"groupId\":" + gid + "}", "gc_lead", online), "LEAVE_GROUP_RESPONSE", "SUCCESS"));
        check("handler LEAVE: trưởng nhóm là người cuối cùng rời -> nhóm bị xóa",
                isStatus(call("LEAVE_GROUP", "{\"groupId\":" + gid + "}", "gc_m1", online), "LEAVE_GROUP_RESPONSE", "SUCCESS")
                        && GroupDAO.getGroupById(gid) == null);
    }

    static void testHandlerDeleteGroup() {
        Map<String, PrintWriter> online = new HashMap<>();
        int gid = createViaHandler("Nhóm sẽ xóa", "[\"gc_m1\",\"gc_m2\"]", "gc_lead", online);
        GroupMessageDAO.saveMessage(gid, "gc_m1", "tin nhắn trước khi xóa");

        check("handler DELETE: thành viên thường không được xóa nhóm",
                isStatus(call("DELETE_GROUP", "{\"groupId\":" + gid + "}", "gc_m1", online), "DELETE_GROUP_RESPONSE", "FAILED")
                        && GroupDAO.getGroupById(gid) != null);
        check("handler DELETE: người ngoài nhóm không được xóa nhóm",
                isStatus(call("DELETE_GROUP", "{\"groupId\":" + gid + "}", "gc_out", online), "DELETE_GROUP_RESPONSE", "FAILED")
                        && GroupDAO.getGroupById(gid) != null);
        check("handler DELETE: nhóm không tồn tại -> FAILED",
                isStatus(call("DELETE_GROUP", "{\"groupId\":999999}", "gc_lead", online), "DELETE_GROUP_RESPONSE", "FAILED"));

        Capture m1 = new Capture();
        Capture out = new Capture();
        online.put("gc_m1", m1.writer);
        online.put("gc_out", out.writer);
        check("handler DELETE: trưởng nhóm xóa nhóm -> SUCCESS, xóa nhóm, thành viên và tin nhắn",
                isStatus(call("DELETE_GROUP", "{\"groupId\":" + gid + "}", "gc_lead", online), "DELETE_GROUP_RESPONSE", "SUCCESS")
                        && GroupDAO.getGroupById(gid) == null
                        && GroupDAO.getRole(gid, "gc_m1") == null
                        && GroupMessageDAO.getHistory(gid, 50).isEmpty());

        JsonObject deleted = m1.find("GROUP_DELETED");
        check("handler DELETE: thành viên online nhận GROUP_DELETED kèm tên nhóm và người xóa",
                deleted != null
                        && deleted.getAsJsonObject("data").get("groupId").getAsInt() == gid
                        && "Nhóm sẽ xóa".equals(deleted.getAsJsonObject("data").get("groupName").getAsString())
                        && "gc_lead".equals(deleted.getAsJsonObject("data").get("deletedBy").getAsString()));
        check("handler DELETE: người ngoài nhóm không nhận GROUP_DELETED", out.find("GROUP_DELETED") == null);
        check("handler DELETE: xóa lại nhóm đã xóa -> FAILED",
                isStatus(call("DELETE_GROUP", "{\"groupId\":" + gid + "}", "gc_lead", online), "DELETE_GROUP_RESPONSE", "FAILED"));
    }

    static void testHandlerMessaging() {
        Map<String, PrintWriter> online = new HashMap<>();
        int gid = createViaHandler("Nhóm nhắn tin", "[\"gc_m1\",\"gc_m2\"]", "gc_lead", online);

        Capture lead = new Capture();
        Capture m1 = new Capture();
        Capture m2 = new Capture(); // m2 offline (không đưa vào online)
        online.put("gc_lead", lead.writer);
        online.put("gc_m1", m1.writer);

        Capture sender = new Capture();
        GroupChatHandler.handleGroupChatAction(
                request("SEND_GROUP_MESSAGE", "{\"groupId\":" + gid + ",\"content\":\"chào cả nhóm\"}"),
                sender.writer, "gc_lead", online);
        check("handler SEND: người gửi nhận SEND_GROUP_MESSAGE_RESPONSE SUCCESS",
                isStatus(sender.last(), "SEND_GROUP_MESSAGE_RESPONSE", "SUCCESS")
                        && "chào cả nhóm".equals(sender.last().getAsJsonObject("data").get("content").getAsString()));
        check("handler SEND: thành viên online khác nhận RECEIVE_GROUP_MESSAGE realtime",
                m1.find("RECEIVE_GROUP_MESSAGE") != null
                        && "gc_lead".equals(m1.find("RECEIVE_GROUP_MESSAGE").getAsJsonObject("data").get("sender").getAsString()));
        check("handler SEND: người gửi không nhận lại bản sao realtime", lead.find("RECEIVE_GROUP_MESSAGE") == null);
        check("handler SEND: thành viên offline không bị ghi gói tin", m2.isEmpty());

        check("handler SEND: người ngoài nhóm gửi -> FAILED",
                isStatus(call("SEND_GROUP_MESSAGE", "{\"groupId\":" + gid + ",\"content\":\"hack\"}", "gc_out", online),
                        "SEND_GROUP_MESSAGE_RESPONSE", "FAILED"));
        check("handler SEND: nội dung rỗng -> FAILED",
                isStatus(call("SEND_GROUP_MESSAGE", "{\"groupId\":" + gid + ",\"content\":\"   \"}", "gc_lead", online),
                        "SEND_GROUP_MESSAGE_RESPONSE", "FAILED"));

        JsonObject hist = call("GET_GROUP_HISTORY", "{\"groupId\":" + gid + ",\"limit\":50}", "gc_m2", online);
        check("handler HISTORY: thành viên (kể cả offline lúc gửi) xem được lịch sử",
                isStatus(hist, "GET_GROUP_HISTORY_RESPONSE", "SUCCESS")
                        && hist.getAsJsonObject("data").getAsJsonArray("messages").size() == 1);
        check("handler HISTORY: người ngoài nhóm không xem được",
                isStatus(call("GET_GROUP_HISTORY", "{\"groupId\":" + gid + "}", "gc_out", online),
                        "GET_GROUP_HISTORY_RESPONSE", "FAILED"));
    }

    static void testIsGroupChatAction() {
        String[] actions = { "CREATE_GROUP", "GET_MY_GROUPS", "GET_GROUP_MEMBERS", "ADD_GROUP_MEMBER",
                "REMOVE_GROUP_MEMBER", "TRANSFER_LEADER", "LEAVE_GROUP", "DELETE_GROUP", "SEND_GROUP_MESSAGE", "GET_GROUP_HISTORY" };
        boolean all = true;
        for (String a : actions) {
            all &= GroupChatHandler.isGroupChatAction(a);
        }
        check("isGroupChatAction: nhận đủ 10 action của module", all && GroupChatHandler.isGroupChatAction("leave_group"));
        check("isGroupChatAction: từ chối action module khác", !GroupChatHandler.isGroupChatAction("SEND_FILE"));
    }
}
