ALTER TABLE chat_messages
    ADD COLUMN message_type VARCHAR(20) NOT NULL DEFAULT 'TEXT';

ALTER TABLE chat_messages
    ADD COLUMN latitude DOUBLE PRECISION;

ALTER TABLE chat_messages
    ADD COLUMN longitude DOUBLE PRECISION;

ALTER TABLE chat_messages
    ADD COLUMN accuracy_meters DOUBLE PRECISION;

ALTER TABLE chat_messages
    ALTER COLUMN content DROP NOT NULL;

ALTER TABLE chat_messages
    DROP CONSTRAINT chk_chat_messages_content;

ALTER TABLE chat_messages
    ADD CONSTRAINT chk_chat_messages_type
        CHECK (message_type IN ('TEXT', 'LOCATION'));

ALTER TABLE chat_messages
    ADD CONSTRAINT chk_chat_messages_payload
        CHECK (
            (
                message_type = 'TEXT'
                AND content IS NOT NULL
                AND CHAR_LENGTH(TRIM(content)) BETWEEN 1 AND 2000
                AND latitude IS NULL
                AND longitude IS NULL
                AND accuracy_meters IS NULL
            )
            OR
            (
                message_type = 'LOCATION'
                AND content IS NULL
                AND latitude IS NOT NULL
                AND latitude BETWEEN -90.0 AND 90.0
                AND longitude IS NOT NULL
                AND longitude BETWEEN -180.0 AND 180.0
                AND accuracy_meters IS NOT NULL
                AND accuracy_meters BETWEEN 0.0 AND 1000000.0
            )
        );
