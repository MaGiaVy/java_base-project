package com.zalolite.common;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Thành viên trong một nhóm chat. */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Member {
    public User user;
    public String role; // OWNER | MEMBER
    public long lastReadMsgId;

    public Member() {
    }

    public Member(User user, String role, long lastReadMsgId) {
        this.user = user;
        this.role = role;
        this.lastReadMsgId = lastReadMsgId;
    }
}
