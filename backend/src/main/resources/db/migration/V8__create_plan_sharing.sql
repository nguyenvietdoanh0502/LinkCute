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
