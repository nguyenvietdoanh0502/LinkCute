ALTER TABLE places ADD COLUMN IF NOT EXISTS overture_id VARCHAR(64);
ALTER TABLE places ADD COLUMN IF NOT EXISTS source_category_raw VARCHAR(255);
ALTER TABLE places ADD COLUMN IF NOT EXISTS overture_category VARCHAR(255);
ALTER TABLE places ADD COLUMN IF NOT EXISTS osm_category VARCHAR(255);
ALTER TABLE places ADD COLUMN IF NOT EXISTS data_source VARCHAR(30);
ALTER TABLE places ADD COLUMN IF NOT EXISTS operating_status VARCHAR(50);
ALTER TABLE places ADD COLUMN IF NOT EXISTS confidence DOUBLE PRECISION;
ALTER TABLE places ADD COLUMN IF NOT EXISTS opening_hours_raw TEXT;

CREATE UNIQUE INDEX IF NOT EXISTS uq_places_overture_id
    ON places(overture_id) WHERE overture_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_places_data_source
    ON places(data_source) WHERE is_deleted = FALSE;
CREATE INDEX IF NOT EXISTS idx_places_source_category_raw
    ON places(source_category_raw) WHERE is_deleted = FALSE AND source_category_raw IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_places_overture_category
    ON places(overture_category) WHERE is_deleted = FALSE AND overture_category IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_places_osm_category
    ON places(osm_category) WHERE is_deleted = FALSE AND osm_category IS NOT NULL;

UPDATE places
SET data_source = 'FULL_DATA'
WHERE source_id IS NOT NULL AND data_source IS NULL;

UPDATE places
SET data_source = 'OSM'
WHERE osm_id IS NOT NULL AND data_source IS NULL;
