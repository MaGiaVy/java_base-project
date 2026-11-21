-- Toàn bộ lược đồ ZaloLite. Chạy tự động khi server khởi động (IF NOT EXISTS nên chạy lại không sao).
CREATE TABLE IF NOT EXISTS users (
    id         BIGSERIAL PRIMARY KEY,
    phone      VARCHAR(15)  NOT NULL UNIQUE,
    email      VARCHAR(120),
    name       VARCHAR(60)  NOT NULL,
    pass_hash  VARCHAR(100) NOT NULL,
    avatar     TEXT,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS friendships (
    user_id    BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    friend_id  BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    status     VARCHAR(10) NOT NULL, -- PENDING | ACCEPTED | BLOCKED
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, friend_id)
);

CREATE TABLE IF NOT EXISTS conversations (
    id         BIGSERIAL PRIMARY KEY,
    is_group   BOOLEAN NOT NULL,
    name       VARCHAR(80),
    owner_id   BIGINT REFERENCES users(id) ON DELETE SET NULL,
    avatar     TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS members (
    conv_id          BIGINT NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
    user_id          BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role             VARCHAR(10) NOT NULL DEFAULT 'MEMBER', -- OWNER | MEMBER
    last_read_msg_id BIGINT NOT NULL DEFAULT 0,
    pinned           BOOLEAN NOT NULL DEFAULT false,
    PRIMARY KEY (conv_id, user_id)
);

CREATE TABLE IF NOT EXISTS messages (
    id         BIGSERIAL PRIMARY KEY,
    conv_id    BIGINT NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
    sender_id  BIGINT REFERENCES users(id) ON DELETE SET NULL,
    kind       VARCHAR(10) NOT NULL, -- TEXT | IMAGE | FILE | SYSTEM
    content    TEXT,
    file_path  TEXT,
    reply_to   BIGINT REFERENCES messages(id) ON DELETE SET NULL,
    recalled   BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_messages_conv ON messages(conv_id, id);

CREATE TABLE IF NOT EXISTS reactions (
    msg_id  BIGINT NOT NULL REFERENCES messages(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    emoji   VARCHAR(16) NOT NULL,
    PRIMARY KEY (msg_id, user_id)
);

CREATE TABLE IF NOT EXISTS otp (
    email      VARCHAR(120) PRIMARY KEY,
    code       VARCHAR(10)  NOT NULL,
    expires_at TIMESTAMPTZ  NOT NULL
);
