ALTER TABLE users
    ADD COLUMN IF NOT EXISTS status VARCHAR(20);

-- Rows created before account status was introduced were already usable accounts.
UPDATE users
SET status = 'ACTIVE'
WHERE status IS NULL;

ALTER TABLE users
    ALTER COLUMN status SET DEFAULT 'PENDING',
    ALTER COLUMN status SET NOT NULL;
