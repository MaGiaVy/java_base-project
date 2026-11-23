package com.zalolite.common;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PacketTest {
    @Test
    void roundTripKeepsTypeReqIdAndData() throws Exception {
        Packet p = new Packet(PacketType.LOGIN).put("phone", "0901234567").put("n", 5);
        p.reqId = 42L;
        String json = p.toJson();
        assertFalse(json.contains("\n"), "mỗi gói phải nằm trên 1 dòng");

        Packet back = Packet.fromJson(json);
        assertEquals(PacketType.LOGIN, back.type);
        assertEquals(42L, back.reqId);
        assertEquals("0901234567", back.str("phone"));
        assertEquals(5, back.num("n"));
    }

    @Test
    void userObjectSurvivesRoundTrip() throws Exception {
        Packet p = Packet.ok(new Packet(PacketType.LOGIN)).put("user", new User(7, "0900000000", "Vỹ", null, null));
        User u = Packet.fromJson(p.toJson()).get("user", User.class);
        assertEquals(7, u.id);
        assertEquals("Vỹ", u.name);
    }

    @Test
    void rejectsPacketWithoutType() {
        assertThrows(IllegalArgumentException.class, () -> Packet.fromJson("{\"data\":{}}"));
    }

    @Test
    void conversationMessageAndListSurviveRoundTrip() throws Exception {
        Conversation c = new Conversation();
        c.id = 10;
        c.isGroup = true;
        c.name = "Nhóm Đồ Án";
        c.unreadCount = 3;
        c.pinned = true;

        Message m = new Message();
        m.id = 99;
        m.convId = 10;
        m.senderId = 1;
        m.senderName = "An";
        m.kind = Message.KIND_AUDIO;
        m.fileName = "voice.wav";
        m.fileSize = 12345;
        m.durationSec = 5;
        m.transport = "P2P";
        m.reactions.put(2L, "❤️");

        Packet p = new Packet(PacketType.MSG_HISTORY)
                .put("hasMore", true)
                .put("conversations", List.of(c))
                .put("messages", List.of(m));

        Packet back = Packet.fromJson(p.toJson());
        assertTrue(back.bool("hasMore"));
        List<Conversation> convs = back.getList("conversations", Conversation.class);
        assertEquals(1, convs.size());
        assertEquals("Nhóm Đồ Án", convs.get(0).name);
        assertTrue(convs.get(0).pinned);

        List<Message> msgs = back.getList("messages", Message.class);
        assertEquals(1, msgs.size());
        assertEquals("P2P", msgs.get(0).transport);
        assertEquals(5, msgs.get(0).durationSec);
        assertEquals("❤️", msgs.get(0).reactions.get(2L));
    }
}
