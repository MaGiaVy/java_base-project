package com.zalolite.server;

import com.zalolite.common.Packet;
import com.zalolite.common.PacketType;
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
            st.execute("TRUNCATE users RESTART IDENTITY CASCADE");
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

    /** Client tối giản: gửi 1 gói, đọc 1 dòng trả lời. */
    static class TestClient implements Closeable {
        final SSLSocket s;
        final BufferedReader in;
        final Writer out;
        long seq;

        TestClient() throws IOException {
            s = (SSLSocket) clientSsl.getSocketFactory().createSocket("localhost", server.getLocalPort());
            in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
            out = new OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8);
        }

        Packet call(Packet p) throws Exception {
            p.reqId = ++seq;
            out.write(p.toJson() + "\n");
            out.flush();
            Packet r = Packet.fromJson(in.readLine());
            assertEquals(p.reqId, r.reqId);
            return r;
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
}
