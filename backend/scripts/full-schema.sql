-- Consolidated Database Schema for Roamly
-- Note: This is for reference. Use Flyway migrations for actual database updates.

-- 1. Users Table
CREATE TABLE users (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email           VARCHAR(255) UNIQUE NOT NULL,
    password_hash   VARCHAR(255),
    full_name       VARCHAR(100) NOT NULL,
    avatar_url      TEXT,
    pin_code        VARCHAR(10) UNIQUE NOT NULL,
    google_id       VARCHAR(255) UNIQUE,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    is_deleted      BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_users_email ON users(email) WHERE is_deleted = FALSE;
CREATE INDEX idx_users_pin_code ON users(pin_code);
CREATE INDEX idx_users_full_name ON users(full_name) WHERE is_deleted = FALSE;

-- 2. Friendships Table
CREATE TABLE friendships (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
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

-- 3. Groups Table
CREATE TABLE groups (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name            VARCHAR(100) NOT NULL,
    description     TEXT,
    cover_url       TEXT,
    created_by      UUID NOT NULL REFERENCES users(id),
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_by      UUID REFERENCES users(id),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    is_deleted      BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_groups_created_by ON groups(created_by) WHERE is_deleted = FALSE;

-- 4. Group Members Table
CREATE TABLE group_members (
    group_id        UUID NOT NULL REFERENCES groups(id),
    user_id         UUID NOT NULL REFERENCES users(id),
    role            VARCHAR(10) NOT NULL DEFAULT 'MEMBER',
    joined_at       TIMESTAMP NOT NULL DEFAULT NOW(),
    added_by        UUID REFERENCES users(id),
    
    PRIMARY KEY (group_id, user_id)
);

CREATE INDEX idx_group_members_user_id ON group_members(user_id);

-- 5. Plans Table
CREATE TABLE plans (
    id                  UUID PRIMARY KEY,
    owner_id            UUID NOT NULL,
    client_plan_id      VARCHAR(128) NOT NULL,
    name                VARCHAR(200) NOT NULL,
    planned_date        DATE NOT NULL,
    client_updated_at   TIMESTAMP NOT NULL,
    version             BIGINT NOT NULL DEFAULT 0,
    created_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_plans_owner
        FOREIGN KEY (owner_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT chk_plans_version
        CHECK (version >= 0),
    CONSTRAINT uq_plans_owner_client_plan
        UNIQUE (owner_id, client_plan_id)
);

CREATE INDEX idx_plans_owner_updated
    ON plans(owner_id, updated_at DESC);

-- 6. Plan Items Table
CREATE TABLE plan_items (
    id                  UUID PRIMARY KEY,
    plan_id             UUID NOT NULL,
    client_item_id      VARCHAR(128) NOT NULL,
    position            INT NOT NULL,
    place_id            UUID NOT NULL,
    place_name          VARCHAR(255) NOT NULL,
    place_address       TEXT,
    place_district      VARCHAR(100),
    place_category      VARCHAR(50) NOT NULL,
    place_photo_url     TEXT,
    place_lat           DOUBLE PRECISION,
    place_lng           DOUBLE PRECISION,
    place_price_level   INT,
    place_price_min     DOUBLE PRECISION,
    place_price_max     DOUBLE PRECISION,
    start_time          TIME,
    end_time            TIME,

    CONSTRAINT fk_plan_items_plan
        FOREIGN KEY (plan_id) REFERENCES plans(id) ON DELETE CASCADE,
    CONSTRAINT fk_plan_items_place
        FOREIGN KEY (place_id) REFERENCES places(id),
    CONSTRAINT chk_plan_items_position
        CHECK (position >= 0),
    CONSTRAINT chk_plan_items_latitude
        CHECK (place_lat IS NULL OR (place_lat >= -90 AND place_lat <= 90)),
    CONSTRAINT chk_plan_items_longitude
        CHECK (place_lng IS NULL OR (place_lng >= -180 AND place_lng <= 180)),
    CONSTRAINT chk_plan_items_price_level
        CHECK (place_price_level IS NULL OR place_price_level >= 0),
    CONSTRAINT chk_plan_items_price_range
        CHECK (
            (place_price_min IS NULL OR place_price_min >= 0)
            AND (place_price_max IS NULL OR place_price_max >= 0)
            AND (
                place_price_min IS NULL
                OR place_price_max IS NULL
                OR place_price_max >= place_price_min
            )
        ),
    CONSTRAINT chk_plan_items_time_range
        CHECK (start_time IS NULL OR end_time IS NULL OR end_time > start_time),
    CONSTRAINT uq_plan_items_client_item
        UNIQUE (plan_id, client_item_id),
    CONSTRAINT uq_plan_items_place
        UNIQUE (plan_id, place_id)
);

CREATE INDEX idx_plan_items_plan_position
    ON plan_items(plan_id, position);
CREATE INDEX idx_plan_items_place
    ON plan_items(place_id);

-- 7. Plan Invitations Table
CREATE TABLE plan_invitations (
    id                  UUID PRIMARY KEY,
    plan_id             UUID NOT NULL,
    inviter_id          UUID NOT NULL,
    invitee_id          UUID NOT NULL,
    status              VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    sent_at             TIMESTAMP NOT NULL DEFAULT NOW(),
    responded_at        TIMESTAMP,
    created_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_plan_invitations_plan
        FOREIGN KEY (plan_id) REFERENCES plans(id) ON DELETE CASCADE,
    CONSTRAINT fk_plan_invitations_inviter
        FOREIGN KEY (inviter_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_plan_invitations_invitee
        FOREIGN KEY (invitee_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT chk_plan_invitations_not_self
        CHECK (inviter_id <> invitee_id),
    CONSTRAINT chk_plan_invitations_status
        CHECK (status IN ('PENDING', 'ACCEPTED', 'DECLINED', 'CANCELLED')),
    CONSTRAINT chk_plan_invitations_response_time
        CHECK (
            (status = 'PENDING' AND responded_at IS NULL)
            OR (status <> 'PENDING' AND responded_at IS NOT NULL)
        ),
    CONSTRAINT uq_plan_invitations_plan_invitee
        UNIQUE (plan_id, invitee_id)
);

CREATE INDEX idx_plan_invitations_invitee_status_sent
    ON plan_invitations(invitee_id, status, sent_at DESC);
CREATE INDEX idx_plan_invitations_plan_status_sent
    ON plan_invitations(plan_id, status, sent_at DESC);
CREATE INDEX idx_plan_invitations_inviter_sent
    ON plan_invitations(inviter_id, sent_at DESC);

-- 8. Plan Members Table
CREATE TABLE plan_members (
    id                  UUID PRIMARY KEY,
    plan_id             UUID NOT NULL,
    user_id             UUID NOT NULL,
    invitation_id       UUID,
    joined_at           TIMESTAMP NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_plan_members_plan
        FOREIGN KEY (plan_id) REFERENCES plans(id) ON DELETE CASCADE,
    CONSTRAINT fk_plan_members_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_plan_members_invitation
        FOREIGN KEY (invitation_id) REFERENCES plan_invitations(id) ON DELETE SET NULL,
    CONSTRAINT uq_plan_members_invitation
        UNIQUE (invitation_id),
    CONSTRAINT uq_plan_members_plan_user
        UNIQUE (plan_id, user_id)
);

CREATE INDEX idx_plan_members_user_joined
    ON plan_members(user_id, joined_at DESC);
CREATE INDEX idx_plan_members_plan_joined
    ON plan_members(plan_id, joined_at);

-- 9. Messages Table
CREATE TABLE messages (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id        UUID NOT NULL REFERENCES groups(id),
    sender_id       UUID NOT NULL REFERENCES users(id),
    type            VARCHAR(10) NOT NULL DEFAULT 'TEXT',
    content         TEXT NOT NULL,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_messages_group_created ON messages(group_id, created_at DESC);
