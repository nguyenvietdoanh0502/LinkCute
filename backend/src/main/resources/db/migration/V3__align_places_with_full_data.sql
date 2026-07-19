ALTER TABLE places ADD COLUMN IF NOT EXISTS source_id VARCHAR(64);
ALTER TABLE places ADD COLUMN IF NOT EXISTS source_category VARCHAR(50);
ALTER TABLE places ADD COLUMN IF NOT EXISTS website TEXT;
ALTER TABLE places ADD COLUMN IF NOT EXISTS scanned_at_lat DOUBLE PRECISION;
ALTER TABLE places ADD COLUMN IF NOT EXISTS scanned_at_lng DOUBLE PRECISION;
ALTER TABLE places ADD COLUMN IF NOT EXISTS search_text TEXT;

CREATE UNIQUE INDEX IF NOT EXISTS uq_places_source_id ON places(source_id) WHERE source_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_places_source_category ON places(source_category)
    WHERE is_deleted = FALSE AND source_category IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_places_scanned_coordinates ON places(scanned_at_lat, scanned_at_lng);
