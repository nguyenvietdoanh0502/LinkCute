CREATE TABLE places (
    id                  UUID PRIMARY KEY,
    osm_id              VARCHAR(255) UNIQUE,
    google_place_id     VARCHAR(255) UNIQUE,
    name                VARCHAR(255) NOT NULL,
    address             TEXT,
    district            VARCHAR(100),
    lat                 DOUBLE PRECISION NOT NULL,
    lng                 DOUBLE PRECISION NOT NULL,
    category            VARCHAR(50) NOT NULL,
    phone               VARCHAR(50),
    price_level         INT,
    price_min           DOUBLE PRECISION,
    price_max           DOUBLE PRECISION,
    rating              DOUBLE PRECISION,
    user_ratings_total  INT,
    rating_source       VARCHAR(50),
    enriched            BOOLEAN NOT NULL DEFAULT FALSE,
    last_synced_at      TIMESTAMP,
    created_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    is_deleted          BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE opening_hours (
    id                  UUID PRIMARY KEY,
    place_id            UUID NOT NULL REFERENCES places(id) ON DELETE CASCADE,
    day_of_week         INT NOT NULL CHECK (day_of_week >= 0 AND day_of_week <= 6),
    open_time           TIME NOT NULL,
    close_time          TIME NOT NULL,
    crosses_midnight    BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE photos (
    id                  UUID PRIMARY KEY,
    place_id            UUID NOT NULL REFERENCES places(id) ON DELETE CASCADE,
    url                 TEXT,
    google_photo_ref    TEXT,
    width               INT,
    height              INT,
    sort_order          INT NOT NULL DEFAULT 0
);

CREATE TABLE reviews (
    id                      UUID PRIMARY KEY,
    place_id                UUID NOT NULL REFERENCES places(id) ON DELETE CASCADE,
    author_name             VARCHAR(255),
    rating                  INT,
    text                    TEXT,
    relative_time_description VARCHAR(100),
    published_at            TIMESTAMP,
    source                  VARCHAR(50) DEFAULT 'google'
);

-- Indexes for place queries
CREATE INDEX idx_places_lat_lng ON places(lat, lng);
CREATE INDEX idx_places_category ON places(category) WHERE is_deleted = FALSE;
CREATE INDEX idx_places_district ON places(district) WHERE is_deleted = FALSE AND district IS NOT NULL;
CREATE INDEX idx_places_name ON places(name) WHERE is_deleted = FALSE;

-- Indexes for foreign keys
CREATE INDEX idx_opening_hours_place_id ON opening_hours(place_id);
CREATE INDEX idx_photos_place_id ON photos(place_id);
CREATE INDEX idx_reviews_place_id ON reviews(place_id);
