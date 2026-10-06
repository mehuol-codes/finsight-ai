-- Runs on every startup, so every statement must be safe to repeat.

-- Accounts. Passwords are stored as BCrypt hashes only.
CREATE TABLE IF NOT EXISTS app_user (
    id            BIGSERIAL    PRIMARY KEY,
    email         VARCHAR(254) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Chat list and the messages shown in the UI (the LLM's own memory lives in SPRING_AI_CHAT_MEMORY).
CREATE TABLE IF NOT EXISTS chat (
    id         VARCHAR(36)  PRIMARY KEY,
    title      VARCHAR(200) NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Every chat belongs to one user. Chats created before accounts existed have no owner until
-- the first account is registered, which takes them over.
ALTER TABLE chat ADD COLUMN IF NOT EXISTS user_id BIGINT REFERENCES app_user (id) ON DELETE CASCADE;
CREATE INDEX IF NOT EXISTS chat_user_id_updated_at_idx ON chat (user_id, updated_at DESC);

CREATE TABLE IF NOT EXISTS chat_message (
    id         BIGSERIAL    PRIMARY KEY,
    chat_id    VARCHAR(36)  NOT NULL REFERENCES chat (id) ON DELETE CASCADE,
    role       VARCHAR(16)  NOT NULL,
    label      VARCHAR(300),
    content    TEXT         NOT NULL,
    has_image  BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS chat_message_chat_id_idx ON chat_message (chat_id, id);
