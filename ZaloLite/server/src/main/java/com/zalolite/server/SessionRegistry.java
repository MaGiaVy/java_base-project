package com.zalolite.server;

import com.zalolite.common.Packet;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * [XỬ LÝ] Sổ quản lý phiên kết nối trực tuyến (thread-safe với ConcurrentHashMap) trên Server.
 * Lưu 2 ánh xạ quan trọng:
 * 1. sessions: userId -> tập các ClientHandler đang kết nối (hỗ trợ 1 tài khoản đăng nhập cùng lúc nhiều thiết bị).
 * 2. p2p: userId -> cặp (ip, port) cổng P2P mà Client đang lắng nghe để phục vụ chat trực tiếp P2P giữa 2 máy.
 * Đóng vai trò bộ định tuyến đẩy (push) sự kiện realtime tới đúng các Client đang online.
 */
public class SessionRegistry {
    /** Bản ghi lưu địa chỉ IP và cổng TCP lắng nghe P2P của một Client. */
    public record P2pEndpoint(String ip, int port) {
    }

    private final Map<Long, Set<ClientHandler>> sessions = new ConcurrentHashMap<>();
    private final Map<Long, P2pEndpoint> p2p = new ConcurrentHashMap<>();

    /**
     * [XỬ LÝ] Đăng ký một kết nối ClientHandler mới cho userId sau khi LOGIN/REGISTER thành công.
     * @return true nếu đây là phiên đầu tiên (user vừa chuyển từ offline -> online) để Server phát PRESENCE.
     */
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

    /**
     * [XỬ LÝ] Gỡ bỏ một kết nối ClientHandler khi Client LOGOUT hoặc ngắt socket.
     * Nếu không còn phiên nào của userId thì xóa luôn thông tin P2P và trả về true (vừa chuyển online -> offline).
     */
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

    /** [XỬ LÝ] Kiểm tra nhanh userId hiện có ít nhất 1 phiên đang kết nối online hay không. */
    public boolean isOnline(long userId) {
        return sessions.containsKey(userId);
    }

    /** [XỬ LÝ] Lưu địa chỉ IP và cổng TCP P2P do Client đăng ký qua gói P2P_REGISTER. */
    public void registerP2p(long userId, String ip, int port) {
        p2p.put(userId, new P2pEndpoint(ip, port));
    }

    /** [XỬ LÝ] Tra cứu điểm cuối P2P (ip:port) của đối phương khi Client gửi gói P2P_LOOKUP. */
    public P2pEndpoint getP2p(long userId) {
        return p2p.get(userId);
    }

    /** [GỬI] Đẩy (push) một gói tin JSON tới tất cả các thiết bị đang online của một userId. */
    public void sendTo(long userId, Packet p) {
        Set<ClientHandler> set = sessions.get(userId);
        // [GỬI] Gọi ClientHandler.send(p) trên từng kết nối TLS của user
        if (set != null) set.forEach(h -> h.send(p));
    }

    /** [GỬI] Phát gói tin tới toàn bộ danh sách userId (dùng khi gửi tin nhắn mới hoặc cập nhật nhóm cho tất cả thành viên). */
    public void sendToAll(Collection<Long> userIds, Packet p) {
        for (Long id : userIds) sendTo(id, p);
    }

    /** [GỬI] Phát gói tin tới mọi userId trong danh sách ngoại trừ excludeUserId (dùng cho TYPING, SEEN_UPDATE, PRESENCE). */
    public void sendToOthers(Collection<Long> userIds, long excludeUserId, Packet p) {
        for (Long id : userIds) {
            if (id != excludeUserId) sendTo(id, p);
        }
    }
}
