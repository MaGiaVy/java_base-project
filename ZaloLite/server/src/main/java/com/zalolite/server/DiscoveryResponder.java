package com.zalolite.server;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;

/**
 * Listens on UDP (multicast + unicast/broadcast) for LAN discovery probes
 * and replies directly with this server's TCP and file ports.
 */
public class DiscoveryResponder {

    public static final String MULTICAST_GROUP = "239.1.1.90";
    public static final int DEFAULT_DISCOVERY_PORT = 9009;
    public static final String PROBE = "ZALOLITE_DISCOVER";
    public static final String REPLY_PREFIX = "ZALOLITE_SERVER:";

    private final int discoveryPort;
    private final int tcpPort;
    private final int filePort;

    private volatile boolean running;
    private MulticastSocket socket;
    private Thread thread;

    public DiscoveryResponder(int tcpPort, int filePort) {
        this(DEFAULT_DISCOVERY_PORT, tcpPort, filePort);
    }

    public DiscoveryResponder(int discoveryPort, int tcpPort, int filePort) {
        this.discoveryPort = discoveryPort;
        this.tcpPort = tcpPort;
        this.filePort = filePort;
    }

    public synchronized void startAsync() throws IOException {
        if (running) {
            return;
        }
        MulticastSocket ms = new MulticastSocket(discoveryPort);
        joinMulticastSilently(ms);
        this.socket = ms;
        this.running = true;
        this.thread = new Thread(this::listenLoop, "discovery-responder");
        this.thread.setDaemon(true);
        this.thread.start();
    }

    private void joinMulticastSilently(MulticastSocket ms) {
        try {
            InetSocketAddress group = new InetSocketAddress(InetAddress.getByName(MULTICAST_GROUP), ms.getLocalPort());
            boolean joinedAny = false;
            Enumeration<NetworkInterface> ifaces = NetworkInterface.getNetworkInterfaces();
            while (ifaces != null && ifaces.hasMoreElements()) {
                NetworkInterface nif = ifaces.nextElement();
                try {
                    if (nif.isUp() && !nif.isLoopback() && nif.supportsMulticast()) {
                        ms.joinGroup(group, nif);
                        joinedAny = true;
                    }
                } catch (IOException ignored) {
                    // Ignore interfaces that reject multicast join.
                }
            }
            if (!joinedAny) {
                ms.joinGroup(group, null);
            }
        } catch (IOException ignored) {
            // Still responds to unicast/broadcast UDP on the bound port.
        }
    }

    private void listenLoop() {
        byte[] buf = new byte[256];
        byte[] replyBytes = (REPLY_PREFIX + tcpPort + ":" + filePort).getBytes(StandardCharsets.UTF_8);
        while (running) {
            try {
                DatagramPacket packet = new DatagramPacket(buf, buf.length);
                socket.receive(packet);
                String payload = new String(packet.getData(), packet.getOffset(), packet.getLength(), StandardCharsets.UTF_8).trim();
                if (PROBE.equals(payload)) {
                    DatagramPacket reply = new DatagramPacket(replyBytes, replyBytes.length, packet.getAddress(), packet.getPort());
                    socket.send(reply);
                }
            } catch (SocketException e) {
                if (!running) {
                    break;
                }
            } catch (IOException ignored) {
                // Keep listening on transient I/O errors.
            }
        }
    }

    public int getLocalPort() {
        MulticastSocket s = this.socket;
        return s != null ? s.getLocalPort() : discoveryPort;
    }

    public synchronized void stop() {
        running = false;
        if (socket != null) {
            socket.close();
            socket = null;
        }
        if (thread != null) {
            thread.interrupt();
            thread = null;
        }
    }
}
