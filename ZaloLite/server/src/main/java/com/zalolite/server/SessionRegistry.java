package com.zalolite.server;

import com.zalolite.common.Packet;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Ai đang online: userId -> các kết nối (1 người có thể mở nhiều máy). */
public class SessionRegistry {
    private final Map<Long, Set<ClientHandler>> sessions = new ConcurrentHashMap<>();

    public void add(long userId, ClientHandler h) {
        sessions.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet()).add(h);
    }

    public void remove(long userId, ClientHandler h) {
        sessions.computeIfPresent(userId, (k, set) -> {
            set.remove(h);
            return set.isEmpty() ? null : set;
        });
    }

    public boolean isOnline(long userId) {
        return sessions.containsKey(userId);
    }

    /** Đẩy gói tin tới mọi máy của một user (nếu đang online). */
    public void sendTo(long userId, Packet p) {
        Set<ClientHandler> set = sessions.get(userId);
        if (set != null) set.forEach(h -> h.send(p));
    }
}
