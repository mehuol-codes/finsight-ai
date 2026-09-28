-- Chat list and the messages shown in the UI (the LLM's own memory lives in SPRING_AI_CHAT_MEMORY).
CREATE TABLE IF NOT EXISTS chat (
    id         VARCHAR(36)  PRIMARY KEY,
    title      VARCHAR(200) NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

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
