CREATE TABLE chat_conversations (
    id              UUID PRIMARY KEY,
    user_low_id     UUID NOT NULL,
    user_high_id    UUID NOT NULL,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_chat_conversations_user_low
        FOREIGN KEY (user_low_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_chat_conversations_user_high
        FOREIGN KEY (user_high_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT chk_chat_conversations_ordered_pair
        CHECK (user_low_id < user_high_id),
    CONSTRAINT uq_chat_conversations_user_pair
        UNIQUE (user_low_id, user_high_id),
    CONSTRAINT uq_chat_conversations_id_pair
        UNIQUE (id, user_low_id, user_high_id)
);

CREATE INDEX idx_chat_conversations_low_updated
    ON chat_conversations(user_low_id, updated_at DESC);
CREATE INDEX idx_chat_conversations_high_updated
    ON chat_conversations(user_high_id, updated_at DESC);

CREATE TABLE chat_messages (
    id                  UUID PRIMARY KEY,
    conversation_id     UUID NOT NULL,
    sender_id           UUID NOT NULL,
    user_low_id         UUID NOT NULL,
    user_high_id        UUID NOT NULL,
    client_message_id   UUID NOT NULL,
    content             VARCHAR(2000) NOT NULL,
    created_at          TIMESTAMP NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_chat_messages_conversation_pair
        FOREIGN KEY (conversation_id, user_low_id, user_high_id)
        REFERENCES chat_conversations(id, user_low_id, user_high_id) ON DELETE CASCADE,
    CONSTRAINT fk_chat_messages_sender
        FOREIGN KEY (sender_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT chk_chat_messages_sender_member
        CHECK (sender_id = user_low_id OR sender_id = user_high_id),
    CONSTRAINT chk_chat_messages_content
        CHECK (CHAR_LENGTH(TRIM(content)) BETWEEN 1 AND 2000),
    CONSTRAINT uq_chat_messages_client_id
        UNIQUE (conversation_id, sender_id, client_message_id)
);

CREATE INDEX idx_chat_messages_conversation_created
    ON chat_messages(conversation_id, created_at DESC, id DESC);
