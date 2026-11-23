package com.zalolite.server;

import com.zalolite.common.Conversation;
import com.zalolite.common.Message;
import com.zalolite.common.Packet;
import com.zalolite.common.PacketType;
import com.zalolite.common.User;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManagerFactory;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.sql.Connection;
import java.sql.Statement;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Test thật đầu-cuối: server TLS + PostgreSQL (database riêng zalolite_test).
 * Tự bỏ qua nếu chưa có server.properties hoặc không kết nối được PostgreSQL.
 */
class ServerIntegrationTest {
    static ZaloServer server;
    static SSLContext clientSsl;

    @BeforeAll
    static void start() throws Exception {
        ServerConfig cfg;
        try {
            cfg = ServerConfig.load();
        } catch (IOException e) {
            assumeTrue(false, "chưa có server.properties");
            return;
        }
        String url = cfg.get("db.url", "jdbc:postgresql://localhost:5432/zalolite").replaceAll("/[^/?]+(\\?.*)?$", "/zalolite_test");
        Db db = new Db(url, cfg.get("db.user", "postgres"), cfg.get("db.password", ""));
        try {
            db.init();
        } catch (Exception e) {
            assumeTrue(false, "không kết nối được PostgreSQL: " + e.getMessage());
        }
        try (Connection c = db.open(); Statement st = c.createStatement()) {
            st.execute("TRUNCATE users, conversations RESTART IDENTITY CASCADE");
        }

        server = new ZaloServer(0, ZaloServer.loadSsl("certs/server.p12", "zalolite-lab"), db);
        Thread t = new Thread(() -> {
            try {
                server.start();
            } catch (Exception ignored) {
            }
        });
        t.setDaemon(true);
        t.start();
        while (server.getLocalPort() <= 0) Thread.sleep(20);

        KeyStore ts = KeyStore.getInstance("PKCS12");
        try (InputStream in = new FileInputStream("../client/src/main/resources/truststore.p12")) {
            ts.load(in, "changeit".toCharArray());
        }
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(ts);
        clientSsl = SSLContext.getInstance("TLS");
        clientSsl.init(null, tmf.getTrustManagers(), null);
    }

    @AfterAll
    static void stop() throws Exception {
        if (server != null) server.stop();
    }

    /** Client test hỗ trợ cả request/response đồng bộ và hàng đợi sự kiện push. */
    static class TestClient implements Closeable {
        final SSLSocket s;
        final BufferedReader in;
        final Writer out;
        final Queue<Packet> pushes = new ArrayDeque<>();
        long seq;

        TestClient() throws IOException {
            s = (SSLSocket) clientSsl.getSocketFactory().createSocket("localhost", server.getLocalPort());
            s.setSoTimeout(3000);
            in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
            out = new OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8);
        }

        Packet call(Packet p) throws Exception {
            p.reqId = ++seq;
            out.write(p.toJson() + "\n");
            out.flush();
            while (true) {
                Packet r = Packet.fromJson(in.readLine());
                if (PacketType.RESPONSE.equals(r.type)) {
                    assertEquals(p.reqId, r.reqId);
                    return r;
                }
                pushes.add(r);
            }
        }

        Packet nextPush(String expectedType) throws Exception {
            for (var it = pushes.iterator(); it.hasNext(); ) {
                Packet p = it.next();
                if (expectedType.equals(p.type)) {
                    it.remove();
                    return p;
                }
            }
            while (true) {
                Packet r = Packet.fromJson(in.readLine());
                if (expectedType.equals(r.type)) return r;
                pushes.add(r);
            }
        }

        User registerAndLogin(String phone, String name) throws Exception {
            assertTrue(call(new Packet(PacketType.REGISTER)
                    .put("phone", phone).put("name", name).put("email", "").put("password", "matkhau1")).isOk());
            Packet res = call(new Packet(PacketType.LOGIN).put("phone", phone).put("password", "matkhau1"));
            assertTrue(res.isOk());
            return res.get("user", User.class);
        }

        public void close() throws IOException {
            s.close();
        }
    }

    static Packet reg(String phone, String pass) {
        return new Packet(PacketType.REGISTER).put("phone", phone).put("name", "Người Test").put("email", "").put("password", pass);
    }

    @Test
    void registerLoginFlow() throws Exception {
        try (TestClient c = new TestClient()) {
            assertTrue(c.call(reg("0901111111", "matkhau1")).isOk());
            assertEquals("Số điện thoại đã được đăng ký", c.call(reg("0901111111", "matkhau1")).str("error"));
            assertFalse(c.call(reg("123", "matkhau1")).isOk(), "SĐT sai định dạng");
            assertFalse(c.call(reg("0902222222", "123")).isOk(), "mật khẩu quá ngắn");

            assertFalse(c.call(new Packet(PacketType.LOGIN).put("phone", "0901111111").put("password", "sai")).isOk());
            Packet ok = c.call(new Packet(PacketType.LOGIN).put("phone", "0901111111").put("password", "matkhau1"));
            assertTrue(ok.isOk());
            assertEquals("Người Test", ok.data.path("user").path("name").asText());
            assertFalse(ok.data.path("user").has("pass_hash"), "không lộ mật khẩu");
        }
    }

    @Test
    void mustLoginBeforeOtherRequests() throws Exception {
        try (TestClient c = new TestClient()) {
            assertEquals("Chưa đăng nhập", c.call(new Packet(PacketType.LOGOUT)).str("error"));
        }
    }

    @Test
    void friendRequestAcceptListAndPresencePush() throws Exception {
        try (TestClient alice = new TestClient(); TestClient bob = new TestClient()) {
            User uA = alice.registerAndLogin("0911000001", "Alice");
            User uB = bob.registerAndLogin("0911000002", "Bob");

            // Không thể tự kết bạn
            assertFalse(alice.call(new Packet(PacketType.FRIEND_REQUEST).put("targetId", uA.id)).isOk());

            // Alice gửi lời mời cho Bob -> Bob nhận FRIEND_EVENT REQUEST
            assertTrue(alice.call(new Packet(PacketType.FRIEND_REQUEST).put("targetId", uB.id)).isOk());
            Packet reqPush = bob.nextPush(PacketType.FRIEND_EVENT);
            assertEquals("REQUEST", reqPush.str("action"));
            assertEquals(uA.id, reqPush.get("user", User.class).id);

            // Gửi trùng bị từ chối
            assertFalse(alice.call(new Packet(PacketType.FRIEND_REQUEST).put("targetId", uB.id)).isOk());

            // Bob chấp nhận -> Alice nhận FRIEND_EVENT ACCEPTED
            assertTrue(bob.call(new Packet(PacketType.FRIEND_ACCEPT).put("userId", uA.id)).isOk());
            Packet accPush = alice.nextPush(PacketType.FRIEND_EVENT);
            assertEquals("ACCEPTED", accPush.str("action"));
            assertEquals(uB.id, accPush.get("user", User.class).id);

            // Kiểm tra FRIEND_LIST có cờ online = true
            Packet fl = alice.call(new Packet(PacketType.FRIEND_LIST));
            assertTrue(fl.isOk());
            List<User> friends = fl.getList("friends", User.class);
            assertEquals(1, friends.size());
            assertEquals(uB.id, friends.get(0).id);
            assertTrue(friends.get(0).online);

            // Bob đăng xuất -> Alice nhận PRESENCE offline
            assertTrue(bob.call(new Packet(PacketType.LOGOUT)).isOk());
            Packet offPush = alice.nextPush(PacketType.PRESENCE);
            assertEquals(uB.id, offPush.num("userId"));
            assertFalse(offPush.bool("online"));

            // Bob đăng nhập lại -> Alice nhận PRESENCE online
            assertTrue(bob.call(new Packet(PacketType.LOGIN).put("phone", "0911000002").put("password", "matkhau1")).isOk());
            Packet onPush = alice.nextPush(PacketType.PRESENCE);
            assertEquals(uB.id, onPush.num("userId"));
            assertTrue(onPush.bool("online"));
        }
    }

    @Test
    void p2pRegisterLookupAndClearOnLogout() throws Exception {
        try (TestClient a = new TestClient(); TestClient b = new TestClient()) {
            User uA = a.registerAndLogin("0912000011", "Peer A");
            b.registerAndLogin("0912000012", "Peer B");

            Packet regRes = a.call(new Packet(PacketType.P2P_REGISTER).put("port", 45678));
            assertTrue(regRes.isOk());
            assertEquals(45678, regRes.num("port"));
            assertNotNull(regRes.str("ip"));

            Packet lookup = b.call(new Packet(PacketType.P2P_LOOKUP).put("userId", uA.id));
            assertTrue(lookup.isOk());
            assertTrue(lookup.bool("online"));
            assertEquals(45678, lookup.num("port"));
            assertEquals(regRes.str("ip"), lookup.str("ip"));

            // Khi A logout, P2P endpoint bị xóa
            assertTrue(a.call(new Packet(PacketType.LOGOUT)).isOk());
            Packet after = b.call(new Packet(PacketType.P2P_LOOKUP).put("userId", uA.id));
            assertFalse(after.bool("online"));
            assertEquals(0, after.num("port"));
        }
    }

    @Test
    void directChatHistoryPaginationSeenReactRecall() throws Exception {
        try (TestClient a = new TestClient(); TestClient b = new TestClient()) {
            User uA = a.registerAndLogin("0913000001", "Chat A");
            User uB = b.registerAndLogin("0913000002", "Chat B");

            Packet openRes = a.call(new Packet(PacketType.CONV_OPEN_DIRECT).put("peerId", uB.id));
            assertTrue(openRes.isOk());
            Conversation conv = openRes.get("conversation", Conversation.class);
            assertFalse(conv.isGroup);
            assertEquals(uB.id, conv.peerId);

            // Gửi 3 tin nhắn từ A -> B nhận 3 push MSG_NEW
            long[] ids = new long[3];
            for (int i = 0; i < 3; i++) {
                Packet sendRes = a.call(new Packet(PacketType.MSG_SEND)
                        .put("convId", conv.id).put("kind", Message.KIND_TEXT).put("content", "Tin " + (i + 1)));
                assertTrue(sendRes.isOk());
                Message sent = sendRes.get("message", Message.class);
                assertEquals(Message.STATE_DELIVERED, sent.deliveryState);
                ids[i] = sent.id;

                Packet push = b.nextPush(PacketType.MSG_NEW);
                assertEquals("Tin " + (i + 1), push.get("message", Message.class).content);
            }

            // Phân trang MSG_HISTORY: lấy 2 tin mới nhất trước, rồi dùng beforeId lấy tin cũ hơn
            Packet page1 = b.call(new Packet(PacketType.MSG_HISTORY).put("convId", conv.id).put("limit", 2));
            assertTrue(page1.isOk());
            assertTrue(page1.bool("hasMore"));
            List<Message> m1 = page1.getList("messages", Message.class);
            assertEquals(2, m1.size());
            assertEquals("Tin 2", m1.get(0).content);
            assertEquals("Tin 3", m1.get(1).content);

            Packet page2 = b.call(new Packet(PacketType.MSG_HISTORY)
                    .put("convId", conv.id).put("beforeId", m1.get(0).id).put("limit", 2));
            assertTrue(page2.isOk());
            assertFalse(page2.bool("hasMore"));
            List<Message> m2 = page2.getList("messages", Message.class);
            assertEquals(1, m2.size());
            assertEquals("Tin 1", m2.get(0).content);

            // B đánh dấu đã xem -> A nhận SEEN_UPDATE và lịch sử của A hiện SEEN
            assertTrue(b.call(new Packet(PacketType.MSG_SEEN).put("convId", conv.id).put("lastReadMsgId", ids[2])).isOk());
            Packet seenPush = a.nextPush(PacketType.SEEN_UPDATE);
            assertEquals(conv.id, seenPush.num("convId"));
            assertEquals(uB.id, seenPush.num("userId"));
            assertEquals(ids[2], seenPush.num("lastReadMsgId"));

            // B thả cảm xúc -> cả hai nhận MSG_REACTED
            assertTrue(b.call(new Packet(PacketType.MSG_REACT).put("msgId", ids[2]).put("emoji", "❤️")).isOk());
            Packet reactPush = a.nextPush(PacketType.MSG_REACTED);
            assertEquals(ids[2], reactPush.num("msgId"));
            assertEquals("❤️", reactPush.str("emoji"));

            // Kiểm tra history có seenCount=1, deliveryState=SEEN, reactions chứa ❤️
            Packet checkHist = a.call(new Packet(PacketType.MSG_HISTORY).put("convId", conv.id).put("limit", 1));
            Message last = checkHist.getList("messages", Message.class).get(0);
            assertEquals(1, last.seenCount);
            assertEquals(Message.STATE_SEEN, last.deliveryState);
            assertEquals("❤️", last.reactions.get(uB.id));

            // A thu hồi tin nhắn cuối -> B nhận MSG_RECALLED
            assertTrue(a.call(new Packet(PacketType.MSG_RECALL).put("msgId", ids[2])).isOk());
            Packet recallPush = b.nextPush(PacketType.MSG_RECALLED);
            assertEquals(ids[2], recallPush.num("msgId"));
        }
    }

    @Test
    void groupPermissionsAndNewMemberHistoryBoundaryG4() throws Exception {
        try (TestClient owner = new TestClient();
             TestClient m1 = new TestClient();
             TestClient m2 = new TestClient();
             TestClient lateJoiner = new TestClient()) {
            User uO = owner.registerAndLogin("0914000001", "Trưởng nhóm");
            User u1 = m1.registerAndLogin("0914000002", "Thành viên 1");
            User u2 = m2.registerAndLogin("0914000003", "Thành viên 2");
            User uLate = lateJoiner.registerAndLogin("0914000004", "Người vào sau");

            // Tạo nhóm 3 người -> thành viên nhận push CONV_UPDATE
            Packet createRes = owner.call(new Packet(PacketType.GROUP_CREATE)
                    .put("name", "Nhóm Lập Trình")
                    .put("memberIds", List.of(u1.id, u2.id)));
            assertTrue(createRes.isOk());
            Conversation g = createRes.get("conversation", Conversation.class);
            assertTrue(g.isGroup);
            assertEquals(uO.id, g.ownerId);
            assertEquals(g.id, m2.nextPush(PacketType.CONV_UPDATE).get("conversation", Conversation.class).id);

            // Chat trước khi thêm người vào sau
            assertTrue(owner.call(new Packet(PacketType.MSG_SEND)
                    .put("convId", g.id).put("kind", Message.KIND_TEXT).put("content", "Tin bí mật trước khi vào")).isOk());

            // Thành viên thường không được xóa người khác
            assertFalse(m1.call(new Packet(PacketType.GROUP_REMOVE_MEMBER)
                    .put("convId", g.id).put("userId", u2.id)).isOk());

            // Trưởng nhóm xóa m2 -> m2 nhận CONV_UPDATE removed=true
            assertTrue(owner.call(new Packet(PacketType.GROUP_REMOVE_MEMBER)
                    .put("convId", g.id).put("userId", u2.id)).isOk());
            Packet rmPush = m2.nextPush(PacketType.CONV_UPDATE);
            assertTrue(rmPush.bool("removed"));
            assertEquals(g.id, rmPush.num("convId"));

            // Đảm bảo mốc thời gian tách biệt rõ ràng trước khi thêm uLate (G4)
            Thread.sleep(20);
            assertTrue(owner.call(new Packet(PacketType.GROUP_ADD_MEMBER)
                    .put("convId", g.id).put("userId", uLate.id)).isOk());

            assertTrue(owner.call(new Packet(PacketType.MSG_SEND)
                    .put("convId", g.id).put("kind", Message.KIND_TEXT).put("content", "Chào mừng thành viên mới")).isOk());

            // G4: uLate chỉ thấy tin nhắn sau thời điểm joined_at, KHÔNG thấy "Tin bí mật trước khi vào"
            Packet lateHist = lateJoiner.call(new Packet(PacketType.MSG_HISTORY).put("convId", g.id).put("limit", 20));
            assertTrue(lateHist.isOk());
            List<Message> msgs = lateHist.getList("messages", Message.class);
            assertTrue(msgs.stream().noneMatch(m -> "Tin bí mật trước khi vào".equals(m.content)),
                    "Thành viên mới không được thấy tin trước khi vào nhóm");
            assertTrue(msgs.stream().anyMatch(m -> "Chào mừng thành viên mới".equals(m.content)),
                    "Thành viên mới phải thấy tin sau khi vào nhóm");
        }
    }
}
