package com.zalolite.server;

import com.zalolite.server.dao.UserDao;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.Socket;
import java.security.KeyStore;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Máy chủ ZaloLite: SSLServerSocket, mỗi client một luồng. */
public class ZaloServer {
    private final int port;
    private final SSLContext ssl;
    private final SessionRegistry sessions = new SessionRegistry();
    private final UserDao users;
    private final ExecutorService pool = Executors.newCachedThreadPool();
    private volatile SSLServerSocket serverSocket;

    public ZaloServer(int port, SSLContext ssl, Db db) {
        this.port = port;
        this.ssl = ssl;
        this.users = new UserDao(db);
    }

    public static SSLContext loadSsl(String keystorePath, String password) throws Exception {
        KeyStore ks = KeyStore.getInstance("PKCS12");
        try (InputStream in = new FileInputStream(keystorePath)) {
            ks.load(in, password.toCharArray());
        }
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(ks, password.toCharArray());
        SSLContext ctx = SSLContext.getInstance("TLS");
        ctx.init(kmf.getKeyManagers(), null, null);
        return ctx;
    }

    public void start() throws Exception {
        serverSocket = (SSLServerSocket) ssl.getServerSocketFactory().createServerSocket(port);
        serverSocket.setEnabledProtocols(new String[]{"TLSv1.3", "TLSv1.2"});
        System.out.println("[INFO] ZaloLite server đang chạy ở cổng " + port + " (TLS)");
        while (!serverSocket.isClosed()) {
            Socket s;
            try {
                s = serverSocket.accept();
            } catch (Exception e) {
                if (serverSocket.isClosed()) break;
                continue;
            }
            pool.submit(new ClientHandler(s, sessions, users));
        }
    }

    public int getLocalPort() {
        return serverSocket == null ? -1 : serverSocket.getLocalPort();
    }

    public void stop() throws Exception {
        if (serverSocket != null) serverSocket.close();
        pool.shutdownNow();
    }

    public static void main(String[] args) throws Exception {
        ServerConfig cfg = ServerConfig.load();
        Db db = Db.fromConfig(cfg);
        db.init();
        SSLContext ssl = loadSsl(cfg.get("keystore.path", "server/certs/server.p12"),
                cfg.get("keystore.password", "zalolite-lab"));
        new ZaloServer(cfg.getInt("port", 9000), ssl, db).start();
    }
}
