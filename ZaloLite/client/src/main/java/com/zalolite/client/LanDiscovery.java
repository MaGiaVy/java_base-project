package com.zalolite.client;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Discovers ZaloLite servers on the local network (and localhost) via UDP probes.
 */
public final class LanDiscovery {

    public static final String MULTICAST_GROUP = "239.1.1.90";
    public static final int DEFAULT_DISCOVERY_PORT = 9009;
    public static final String PROBE = "ZALOLITE_DISCOVER";
    public static final String REPLY_PREFIX = "ZALOLITE_SERVER:";

    public record DiscoveredServer(String host, int tcpPort, int filePort) {
        @Override
        public String toString() {
            return host + ":" + tcpPort;
        }
    }

    private LanDiscovery() {}

    public static List<DiscoveredServer> discover(int timeoutMs) {
        return discover(DEFAULT_DISCOVERY_PORT, timeoutMs);
    }

    public static List<DiscoveredServer> discover(int discoveryPort, int timeoutMs) {
        Map<String, DiscoveredServer> found = new LinkedHashMap<>();
        byte[] probe = PROBE.getBytes(StandardCharsets.UTF_8);

        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setBroadcast(true);
            sendProbe(socket, probe, MULTICAST_GROUP, discoveryPort);
            sendProbe(socket, probe, "255.255.255.255", discoveryPort);
            sendProbe(socket, probe, "127.0.0.1", discoveryPort);

            long deadline = System.currentTimeMillis() + Math.max(1, timeoutMs);
            byte[] buf = new byte[256];
            while (true) {
                long remaining = deadline - System.currentTimeMillis();
                if (remaining <= 0) {
                    break;
                }
                socket.setSoTimeout((int) remaining);
                DatagramPacket packet = new DatagramPacket(buf, buf.length);
                try {
                    socket.receive(packet);
                } catch (SocketTimeoutException e) {
                    break;
                }
                String reply = new String(packet.getData(), packet.getOffset(), packet.getLength(), StandardCharsets.UTF_8).trim();
                DiscoveredServer server = parseReply(packet.getAddress().getHostAddress(), reply);
                if (server != null) {
                    found.putIfAbsent(server.host() + ":" + server.tcpPort(), server);
                }
            }
        } catch (IOException ignored) {
            // Return whatever servers were collected before any socket failure.
        }
        return new ArrayList<>(found.values());
    }

    private static void sendProbe(DatagramSocket socket, byte[] probe, String host, int port) {
        try {
            socket.send(new DatagramPacket(probe, probe.length, InetAddress.getByName(host), port));
        } catch (IOException ignored) {
            // Individual target (e.g., broadcast or multicast) may be unavailable on some interfaces.
        }
    }

    private static DiscoveredServer parseReply(String host, String reply) {
        if (!reply.startsWith(REPLY_PREFIX)) {
            return null;
        }
        String[] parts = reply.substring(REPLY_PREFIX.length()).split(":");
        if (parts.length != 2) {
            return null;
        }
        try {
            int tcpPort = Integer.parseInt(parts[0].trim());
            int filePort = Integer.parseInt(parts[1].trim());
            if (tcpPort <= 0 || filePort <= 0) {
                return null;
            }
            return new DiscoveredServer(host, tcpPort, filePort);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
