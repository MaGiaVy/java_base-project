package com.zalolite.server;

import com.zalolite.common.Packet;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Ai đang online: userId -> các kết nối (1 người có thể mở nhiều máy) và điểm cuối P2P. */
public class SessionRegistry {
    public record P2pEndpoint(String ip, int port) {
    }

    private final Map<Long, Set<ClientHandler>> sessions = new ConcurrentHashMap<>();
    private final Map<Long, P2pEndpoint> p2p = new ConcurrentHashMap<>();

    /** Trả về true nếu user vừa chuyển từ offline sang online. */
    public boolean add(long userId, ClientHandler h) {
        boolean[] becameOnline = {false};
        sessions.compute(userId, (k, set) -> {
            if (set == null) {
                set = ConcurrentHashMap.newKeySet();
                becameOnline[0] = true;
            }
            set.add(h);
            return set;
        });
        return becameOnline[0];
    }

    /** Trả về true nếu user vừa chuyển từ online sang offline (hết phiên hoạt động). */
    public boolean remove(long userId, ClientHandler h) {
        boolean[] becameOffline = {false};
        sessions.computeIfPresent(userId, (k, set) -> {
            set.remove(h);
            if (set.isEmpty()) {
                becameOffline[0] = true;
                p2p.remove(userId);
                return null;
            }
            return set;
        });
        return becameOffline[0];
    }

    public boolean isOnline(long userId) {
        return sessions.containsKey(userId);
    }

    public void registerP2p(long userId, String ip, int port) {
        p2p.put(userId, new P2pEndpoint(ip, port));
    }

    public P2pEndpoint getP2p(long userId) {
        return p2p.get(userId);
    }

    /** Đẩy gói tin tới mọi máy của một user (nếu đang online). */
    public void sendTo(long userId, Packet p) {
        Set<ClientHandler> set = sessions.get(userId);
        if (set != null) set.forEach(h -> h.send(p));
    }

    public void sendToAll(Collection<Long> userIds, Packet p) {
        for (Long id : userIds) sendTo(id, p);
    }

    public void sendToOthers(Collection<Long> userIds, long excludeUserId, Packet p) {
        for (Long id : userIds) {
            if (id != excludeUserId) sendTo(id, p);
        }
    }
}
