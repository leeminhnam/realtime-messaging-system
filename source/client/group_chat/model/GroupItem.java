package group_chat.model;

import com.google.gson.JsonObject;

public class GroupItem {
    private final int id;
    private final String name;
    private final String leader;
    private final int memberCount;

    public GroupItem(int id, String name, String leader, int memberCount) {
        this.id = id;
        this.name = name;
        this.leader = leader;
        this.memberCount = memberCount;
    }

    public static GroupItem fromJson(JsonObject json) {
        return new GroupItem(
                json.get("id").getAsInt(),
                json.get("name").getAsString(),
                json.has("leader") && !json.get("leader").isJsonNull() ? json.get("leader").getAsString() : "",
                json.has("memberCount") ? json.get("memberCount").getAsInt() : 0);
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getLeader() {
        return leader;
    }

    public int getMemberCount() {
        return memberCount;
    }
}
