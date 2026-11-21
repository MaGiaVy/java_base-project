package com.zalolite.client;

import com.zalolite.common.Packet;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManagerFactory;
import javax.swing.SwingUtilities;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Kết nối TLS tới server. Một luồng nền đọc liên tục:
 *  - gói RESPONSE có reqId -> trả cho request đang chờ
 *  - gói khác (sự kiện đẩy: tin nhắn mới, lời mời kết bạn...) -> gọi listener trên luồng Swing
 */
public class Connection {
    private final SSLSocket socket;
    private final BufferedWriter out;
    private final AtomicLong seq = new AtomicLong();
    private final Map<Long, CompletableFuture<Packet>> pending = new ConcurrentHashMap<>();
    private final List<Consumer<Packet>> listeners = new CopyOnWriteArrayList<>();
    private volatile Runnable onDisconnect;

    public Connection(String host, int port) throws Exception {
        socket = (SSLSocket) trustedContext().getSocketFactory().createSocket(host, port);
        socket.startHandshake();
        out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
        Thread reader = new Thread(this::readLoop, "server-reader");
        reader.setDaemon(true);
        reader.start();
    }

    /** Chỉ tin chứng chỉ của server ZaloLite (truststore.p12 đóng gói trong client, chỉ chứa public cert). */
    private static SSLContext trustedContext() throws Exception {
        KeyStore ts = KeyStore.getInstance("PKCS12");
        try (InputStream in = Connection.class.getResourceAsStream("/truststore.p12")) {
            if (in == null) throw new IOException("Thiếu truststore.p12 trong client");
            ts.load(in, "changeit".toCharArray());
        }
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(ts);
        SSLContext ctx = SSLContext.getInstance("TLS");
        ctx.init(null, tmf.getTrustManagers(), null);
        return ctx;
    }

    private void readLoop() {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = in.readLine()) != null) {
                Packet p;
                try {
                    p = Packet.fromJson(line);
                } catch (Exception e) {
                    continue;
                }
                CompletableFuture<Packet> f = p.reqId == null ? null : pending.remove(p.reqId);
                if (f != null) {
                    f.complete(p);
                } else {
                    for (Consumer<Packet> l : listeners) SwingUtilities.invokeLater(() -> l.accept(p));
                }
            }
        } catch (IOException ignored) {
        } finally {
            pending.values().forEach(f -> f.completeExceptionally(new IOException("Mất kết nối server")));
            pending.clear();
            Runnable r = onDisconnect;
            if (r != null) SwingUtilities.invokeLater(r);
        }
    }

    /** Gửi request và chờ trả lời (tối đa 10 giây). Không gọi trên luồng Swing nếu thao tác có thể lâu. */
    public Packet request(Packet p) throws Exception {
        p.reqId = seq.incrementAndGet();
        CompletableFuture<Packet> f = new CompletableFuture<>();
        pending.put(p.reqId, f);
        try {
            send(p);
            return f.get(10, TimeUnit.SECONDS);
        } finally {
            pending.remove(p.reqId);
        }
    }

    public synchronized void send(Packet p) throws IOException {
        out.write(p.toJson());
        out.write('\n');
        out.flush();
    }

    public void addListener(Consumer<Packet> l) {
        listeners.add(l);
    }

    public void setOnDisconnect(Runnable r) {
        onDisconnect = r;
    }

    public void close() {
        onDisconnect = null;
        try {
            socket.close();
        } catch (IOException ignored) {
        }
    }
}
