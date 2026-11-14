package com.example.client;

import com.example.model.ChatMessage;
import com.example.model.MessageType;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

/**
 * Secure TLS Chat Client.
 * Connects to the server over TLS 1.3 / 1.2, validates the server certificate,
 * authenticates users, and supports concurrent message sending and asynchronous receiving.
 */
public class ChatClient {

    public static final int DEFAULT_PORT = 5000;

    private final File certFile;
    private final SSLContext customSslContext;
    private final ReentrantLock sendLock = new ReentrantLock();
    private final AtomicBoolean disconnected = new AtomicBoolean(false);

    private SSLSocket socket;
    private BufferedReader reader;
    private BufferedWriter writer;
    private Thread receiveThread;

    private volatile boolean running = false;
    private volatile boolean authenticated = false;
    private String serverHost;
    private int serverPort;
    private String username;
    private int clientId = -1;
    private String lastErrorMessage;

    public ChatClient() {
        this(findDefaultCertificateFile());
    }

    public ChatClient(File certFile) {
        this.certFile = certFile;
        this.customSslContext = null;
    }

    public ChatClient(String certPath) {
        this(new File(certPath));
    }

    public ChatClient(SSLContext customSslContext) {
        this.certFile = null;
        this.customSslContext = customSslContext;
    }

    /**
     * Resolves the default server.crt certificate location.
     */
    public static File findDefaultCertificateFile() {
        String[] candidatePaths = {
                "client/certificates/server.crt",
                "certificates/server.crt",
                "server/certificates/server.crt",
                "../client/certificates/server.crt",
                "../certificates/server.crt",
                "server.crt"
        };
        for (String path : candidatePaths) {
            File f = new File(path);
            if (f.exists() && f.isFile()) {
                return f;
            }
        }
        return new File("client/certificates/server.crt");
    }

    /**
     * Creates an SSLContext using a custom certificate file as the TrustStore.
     */
    public static SSLContext createSSLContext(File certFile) throws GeneralSecurityException, IOException {
        if (certFile == null || !certFile.exists()) {
            throw new FileNotFoundException("Certificate file not found: " +
                    (certFile != null ? certFile.getAbsolutePath() : "null"));
        }
        try (InputStream in = new FileInputStream(certFile)) {
            return createSSLContext(in);
        }
    }

    /**
     * Creates an SSLContext from an X.509 certificate input stream.
     */
    public static SSLContext createSSLContext(InputStream certStream) throws GeneralSecurityException, IOException {
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        X509Certificate cert = (X509Certificate) cf.generateCertificate(certStream);

        KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
        trustStore.load(null, null);
        trustStore.setCertificateEntry("server-cert", cert);

        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(trustStore);

        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, tmf.getTrustManagers(), new SecureRandom());
        return sslContext;
    }

    /**
     * Connects to the server over TLS and validates server certificate.
     */
    public void connect(String host) throws IOException, GeneralSecurityException {
        connect(host, DEFAULT_PORT);
    }

    /**
     * Connects to the server with specified host and port over TLS.
     */
    public void connect(String host, int port) throws IOException, GeneralSecurityException {
        this.serverHost = host;
        this.serverPort = port;
        this.disconnected.set(false);

        SSLContext context = this.customSslContext != null
                ? this.customSslContext
                : createSSLContext(this.certFile);

        SSLSocketFactory factory = context.getSocketFactory();
        this.socket = (SSLSocket) factory.createSocket(host, port);
        this.socket.setEnabledProtocols(new String[]{"TLSv1.3", "TLSv1.2"});
        this.socket.startHandshake();

        try {
            ServerCertificateValidator.validate(this.socket, host);
        } catch (CertificateException e) {
            try {
                this.socket.close();
            } catch (IOException ignored) {}
            throw e;
        }

        this.reader = new BufferedReader(new InputStreamReader(this.socket.getInputStream(), StandardCharsets.UTF_8));
        this.writer = new BufferedWriter(new OutputStreamWriter(this.socket.getOutputStream(), StandardCharsets.UTF_8));
        this.running = true;
    }

    /**
     * Sends LOGIN message and waits for LOGIN_RESULT.
     *
     * @param username user identifier
     * @param password user password
     * @return true if authenticated, false otherwise
     * @throws IOException on network or parse error
     */
    public boolean login(String username, String password) throws IOException {
        if (socket == null || socket.isClosed()) {
            throw new IOException("Cannot login: socket is not connected");
        }

        ChatMessage loginMsg = ChatMessage.createLogin(username, password);
        send(loginMsg);

        String responseLine = reader.readLine();
        if (responseLine == null) {
            this.lastErrorMessage = "Server disconnected during authentication";
            disconnect();
            return false;
        }

        ChatMessage result = ChatMessage.fromJson(responseLine);
        if (MessageType.LOGIN_RESULT.getValue().equalsIgnoreCase(result.getType())) {
            if (Boolean.TRUE.equals(result.getSuccess())) {
                this.authenticated = true;
                this.username = username;
                this.clientId = result.getClientId() != null ? result.getClientId() : -1;
                return true;
            } else {
                this.lastErrorMessage = result.getContent() != null ? result.getContent() : "Authentication failed";
                disconnect();
                return false;
            }
        } else {
            this.lastErrorMessage = "Unexpected message received: " + result.getType();
            disconnect();
            return false;
        }
    }

    /**
     * Sends a chat message. Must be authenticated.
     */
    public void sendMessage(String content) throws IOException {
        if (!authenticated) {
            throw new IllegalStateException("Client is not authenticated");
        }
        ChatMessage msg = ChatMessage.createMessage(this.username, content);
        send(msg);
    }

    /**
     * Thread-safe send of a ChatMessage JSON payload ending with newline.
     */
    public void send(ChatMessage message) throws IOException {
        if (socket == null || socket.isClosed() || writer == null) {
            throw new IOException("Socket is closed or not connected");
        }
        String json = message.toJson();
        sendLock.lock();
        try {
            writer.write(json);
            writer.flush();
        } finally {
            sendLock.unlock();
        }
    }

    /**
     * Starts continuous background message receiving loop.
     *
     * @param onMessageReceived callback when a message is received
     * @param onDisconnected    callback when connection is lost or closed
     */
    public void startReceiveLoop(Consumer<ChatMessage> onMessageReceived, Runnable onDisconnected) {
        if (receiveThread != null && receiveThread.isAlive()) {
            return;
        }

        this.receiveThread = new Thread(() -> {
            try {
                String line;
                while (running && (line = reader.readLine()) != null) {
                    if (line.trim().isEmpty()) {
                        continue;
                    }
                    try {
                        ChatMessage msg = ChatMessage.fromJson(line);
                        if (onMessageReceived != null) {
                            onMessageReceived.accept(msg);
                        }
                    } catch (Exception ex) {
                        System.err.println("[ChatClient] JSON parsing error: " + ex.getMessage());
                    }
                }
            } catch (IOException ignored) {
                // Socket closed or connection reset
            } finally {
                disconnect();
                if (onDisconnected != null) {
                    try {
                        onDisconnected.run();
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                }
            }
        }, "ChatClient-Receiver-" + (username != null ? username : "guest"));

        this.receiveThread.setDaemon(true);
        this.receiveThread.start();
    }

    /**
     * Safely disconnects the client and closes all resources.
     */
    public void disconnect() {
        if (!disconnected.compareAndSet(false, true)) {
            return;
        }
        running = false;
        try {
            if (socket != null && !socket.isClosed() && authenticated) {
                try {
                    send(ChatMessage.createDisconnect());
                } catch (Exception ignored) {}
            }
        } finally {
            authenticated = false;
            try {
                if (writer != null) {
                    writer.close();
                }
            } catch (Exception ignored) {}
            try {
                if (reader != null) {
                    reader.close();
                }
            } catch (Exception ignored) {}
            try {
                if (socket != null && !socket.isClosed()) {
                    socket.close();
                }
            } catch (Exception ignored) {}
        }
    }

    // Getters
    public boolean isConnected() {
        return socket != null && socket.isConnected() && !socket.isClosed();
    }

    public boolean isAuthenticated() {
        return authenticated;
    }

    public SSLSocket getSocket() {
        return socket;
    }

    public String getServerHost() {
        return serverHost;
    }

    public int getServerPort() {
        return serverPort;
    }

    public String getUsername() {
        return username;
    }

    public int getClientId() {
        return clientId;
    }

    public String getLastErrorMessage() {
        return lastErrorMessage;
    }

    public String getTlsProtocol() {
        if (socket != null && socket.isConnected()) {
            try {
                return socket.getSession().getProtocol();
            } catch (Exception ignored) {}
        }
        return "TLS";
    }

    public String getCipherSuite() {
        if (socket != null && socket.isConnected()) {
            try {
                return socket.getSession().getCipherSuite();
            } catch (Exception ignored) {}
        }
        return "UNKNOWN";
    }
}
