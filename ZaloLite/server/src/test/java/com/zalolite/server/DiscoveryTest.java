package com.zalolite.server;

import org.junit.jupiter.api.Test;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DiscoveryTest {

    @Test
    void respondsToDiscoveryProbe() throws Exception {
        DiscoveryResponder responder = new DiscoveryResponder(0, 9000, 9001);
        responder.startAsync();
        try (DatagramSocket client = new DatagramSocket()) {
            client.setSoTimeout(2000);
            byte[] probe = DiscoveryResponder.PROBE.getBytes(StandardCharsets.UTF_8);
            InetAddress localhost = InetAddress.getByName("127.0.0.1");
            client.send(new DatagramPacket(probe, probe.length, localhost, responder.getLocalPort()));

            byte[] buf = new byte[256];
            DatagramPacket reply = new DatagramPacket(buf, buf.length);
            client.receive(reply);
            String text = new String(reply.getData(), reply.getOffset(), reply.getLength(), StandardCharsets.UTF_8).trim();
            assertEquals("ZALOLITE_SERVER:9000:9001", text);
        } finally {
            responder.stop();
        }
    }
}
