CREATE TABLE users (
    id              UUID PRIMARY KEY,
    email           VARCHAR(255) UNIQUE NOT NULL,
    password_hash   VARCHAR(255),           -- NULL nếu đăng nhập Google
    full_name       VARCHAR(100) NOT NULL,
    avatar_url      TEXT,
    pin_code        VARCHAR(10) UNIQUE NOT NULL, -- VD: RML-123456
    google_id       VARCHAR(255) UNIQUE,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    is_deleted      BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_users_email ON users(email) WHERE is_deleted = FALSE;
CREATE INDEX idx_users_pin_code ON users(pin_code);
CREATE INDEX idx_users_full_name ON users(full_name) WHERE is_deleted = FALSE;
