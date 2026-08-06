CREATE TABLE friendships (
    id              UUID PRIMARY KEY,
    user_low_id     UUID NOT NULL,
    user_high_id    UUID NOT NULL,
    requester_id    UUID NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    accepted_at     TIMESTAMP,

    CONSTRAINT fk_friendships_user_low
        FOREIGN KEY (user_low_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_friendships_user_high
        FOREIGN KEY (user_high_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_friendships_requester
        FOREIGN KEY (requester_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT chk_friendships_ordered_pair
        CHECK (user_low_id < user_high_id),
    CONSTRAINT chk_friendships_requester_member
        CHECK (requester_id = user_low_id OR requester_id = user_high_id),
    CONSTRAINT chk_friendships_status
        CHECK (status IN ('PENDING', 'ACCEPTED')),
    CONSTRAINT chk_friendships_accepted_at
        CHECK (
            (status = 'PENDING' AND accepted_at IS NULL)
            OR (status = 'ACCEPTED' AND accepted_at IS NOT NULL)
        ),
    CONSTRAINT uq_friendships_user_pair UNIQUE (user_low_id, user_high_id)
);

CREATE INDEX idx_friendships_low_status_updated
    ON friendships(user_low_id, status, updated_at DESC);
CREATE INDEX idx_friendships_high_status_updated
    ON friendships(user_high_id, status, updated_at DESC);
CREATE INDEX idx_friendships_requester_status_updated
    ON friendships(requester_id, status, updated_at DESC);
