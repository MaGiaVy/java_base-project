package com.zalolite.common;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Thông tin công khai của người dùng (không bao giờ chứa mật khẩu). */
@JsonIgnoreProperties(ignoreUnknown = true)
public class User {
    public long id;
    public String phone;
    public String name;
    public String email;
    public String avatar;
    public boolean online;

    public User() {
    }

    public User(long id, String phone, String name, String email, String avatar) {
        this.id = id;
        this.phone = phone;
        this.name = name;
        this.email = email;
        this.avatar = avatar;
    }

    @Override
    public String toString() {
        return name + " (" + phone + ")";
    }
}
