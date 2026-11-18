package com.iot;

import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

/**
 * Lớp quản lý kết nối và giao tiếp MQTT Client sử dụng Eclipse Paho
 */
public class MqttManager {

    public interface MqttListener {
        void onConnected(String brokerUrl);
        void onConnectionLost(String reason);
        void onMessage(String topic, String payload);
    }

    private MqttClient client;
    private MqttListener listener;

    public MqttManager(MqttListener listener) {
        this.listener = listener;
    }

    public synchronized void connect(String brokerUrl, String clientId, String username, String password) throws MqttException {
        if (client != null && client.isConnected()) {
            client.disconnect();
            client.close();
        }

        String effectiveUrl = brokerUrl;
        if ("tcp://localhost:1883".equalsIgnoreCase(brokerUrl)
                || "tcp://127.0.0.1:1883".equalsIgnoreCase(brokerUrl)) {
            try (java.net.Socket probe = new java.net.Socket()) {
                probe.connect(new java.net.InetSocketAddress("127.0.0.2", 1883), 150);
                effectiveUrl = "tcp://127.0.0.2:1883";
            } catch (Exception ignored) {
            }
        }

        client = new MqttClient(effectiveUrl, clientId, new MemoryPersistence());

        MqttConnectOptions options = new MqttConnectOptions();
        options.setCleanSession(true);
        options.setConnectionTimeout(5);
        options.setKeepAliveInterval(20);
        options.setAutomaticReconnect(true);

        if (username != null && !username.trim().isEmpty()) {
            options.setUserName(username.trim());
        }
        if (password != null && !password.trim().isEmpty()) {
            options.setPassword(password.trim().toCharArray());
        }

        client.setCallback(new MqttCallbackExtended() {
            @Override
            public void connectComplete(boolean reconnect, String serverURI) {
                if (listener != null) {
                    listener.onConnected(serverURI);
                }
            }

            @Override
            public void connectionLost(Throwable cause) {
                if (listener != null) {
                    listener.onConnectionLost(cause != null ? cause.getMessage() : "Unknown");
                }
            }

            @Override
            public void messageArrived(String topic, MqttMessage message) {
                if (listener != null) {
                    listener.onMessage(topic, new String(message.getPayload()));
                }
            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken token) {
                // Not used
            }
        });

        client.connect(options);
    }

    public synchronized void disconnect() {
        try {
            if (client != null && client.isConnected()) {
                client.disconnect();
                client.close();
            }
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    public synchronized void subscribe(String topic, int qos) throws MqttException {
        if (client != null && client.isConnected()) {
            client.subscribe(topic, qos);
        }
    }

    public synchronized void publish(String topic, String payload, int qos) throws MqttException {
        if (client != null && client.isConnected()) {
            MqttMessage msg = new MqttMessage(payload.getBytes());
            msg.setQos(qos);
            msg.setRetained(false);
            client.publish(topic, msg);
        } else {
            throw new MqttException(MqttException.REASON_CODE_CLIENT_NOT_CONNECTED);
        }
    }

    public synchronized boolean isConnected() {
        return client != null && client.isConnected();
    }
}
