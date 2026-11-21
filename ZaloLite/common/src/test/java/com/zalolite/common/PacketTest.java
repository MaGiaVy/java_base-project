package com.zalolite.common;

import org.junit.jupiter.api.Test;

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
}
