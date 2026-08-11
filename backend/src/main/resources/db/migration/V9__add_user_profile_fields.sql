ALTER TABLE users
    ADD COLUMN gender VARCHAR(20) NOT NULL DEFAULT 'UNSPECIFIED';

ALTER TABLE users
    ADD COLUMN birth_year INTEGER;

ALTER TABLE users
    ADD COLUMN address VARCHAR(255);

ALTER TABLE users
    ADD COLUMN avatar_public_id VARCHAR(255);

ALTER TABLE users
    ADD COLUMN row_version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE users
    ADD CONSTRAINT chk_users_gender
        CHECK (gender IN ('UNSPECIFIED', 'MALE', 'FEMALE', 'OTHER'));

ALTER TABLE users
    ADD CONSTRAINT chk_users_birth_year
        CHECK (birth_year IS NULL OR birth_year BETWEEN 1900 AND 2100);

ALTER TABLE users
    ADD CONSTRAINT chk_users_row_version
        CHECK (row_version >= 0);
