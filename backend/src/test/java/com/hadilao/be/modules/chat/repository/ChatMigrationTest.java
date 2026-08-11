package com.hadilao.be.modules.chat.repository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.Sql;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:chat_migration;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.flyway.enabled=false"
})
@Sql(statements = "CREATE TABLE users (id UUID PRIMARY KEY)")
@Sql(scripts = "classpath:db/migration/V10__create_direct_chat.sql")
@Sql(statements = {
        """
                INSERT INTO users (id) VALUES
                    (CAST('00000000-0000-0000-0000-000000000001' AS UUID)),
                    (CAST('00000000-0000-0000-0000-000000000002' AS UUID));
                """,
        """
                INSERT INTO chat_conversations (
                    id, user_low_id, user_high_id, created_at, updated_at
                ) VALUES (
                    CAST('00000000-0000-0000-0000-000000000101' AS UUID),
                    CAST('00000000-0000-0000-0000-000000000001' AS UUID),
                    CAST('00000000-0000-0000-0000-000000000002' AS UUID),
                    TIMESTAMP '2026-08-10 08:15:30',
                    TIMESTAMP '2026-08-10 08:15:30'
                );
                """,
        """
                INSERT INTO chat_messages (
                    id, conversation_id, sender_id, user_low_id, user_high_id,
                    client_message_id, content, created_at
                ) VALUES (
                    CAST('00000000-0000-0000-0000-000000000301' AS UUID),
                    CAST('00000000-0000-0000-0000-000000000101' AS UUID),
                    CAST('00000000-0000-0000-0000-000000000001' AS UUID),
                    CAST('00000000-0000-0000-0000-000000000001' AS UUID),
                    CAST('00000000-0000-0000-0000-000000000002' AS UUID),
                    CAST('00000000-0000-0000-0000-000000000201' AS UUID),
                    'hello',
                    TIMESTAMP '2026-08-10 08:15:30'
                );
                """
})
@Sql(scripts = "classpath:db/migration/V11__add_chat_location_messages.sql")
class ChatMigrationTest {

    private static final UUID USER_LOW = id(1);
    private static final UUID USER_HIGH = id(2);
    private static final UUID OTHER_USER = id(3);
    private static final UUID CONVERSATION = id(101);
    private static final UUID CLIENT_MESSAGE = id(201);
    private static final Instant NOW = Instant.parse("2026-08-10T08:15:30Z");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void migrationEnforcesConversationAndMessageInvariantsAndCascades() {
        jdbcTemplate.update("INSERT INTO users (id) VALUES (?)", OTHER_USER);

        assertThat(count("chat_conversations")).isOne();
        assertThat(count("chat_messages")).isOne();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT message_type FROM chat_messages WHERE id = ?",
                String.class,
                id(301)
        )).isEqualTo("TEXT");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT content FROM chat_messages WHERE id = ?",
                String.class,
                id(301)
        )).isEqualTo("hello");
        assertThat(jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*) FROM chat_messages
                        WHERE id = ?
                          AND latitude IS NULL
                          AND longitude IS NULL
                          AND accuracy_meters IS NULL
                        """,
                Integer.class,
                id(301)
        )).isOne();

        insertTypedMessage(
                id(308),
                CONVERSATION,
                USER_LOW,
                id(208),
                "LOCATION",
                null,
                21.0278,
                105.8342,
                12.5
        );
        assertThat(count("chat_messages")).isEqualTo(2);
        insertTypedMessage(
                id(322), CONVERSATION, USER_LOW, id(222),
                "LOCATION", null, -90.0, -180.0, 0.0
        );
        insertTypedMessage(
                id(323), CONVERSATION, USER_LOW, id(223),
                "LOCATION", null, 90.0, 180.0, 1_000_000.0
        );
        assertThat(count("chat_messages")).isEqualTo(4);

        assertRejected(() -> insertConversation(id(102), USER_LOW, USER_HIGH));
        assertRejected(() -> insertConversation(id(103), USER_HIGH, USER_LOW));
        assertRejected(() -> insertConversation(id(104), OTHER_USER, OTHER_USER));
        assertRejected(() -> insertConversation(id(105), USER_LOW, id(999)));

        assertRejected(() -> insertMessage(
                id(302),
                CONVERSATION,
                USER_LOW,
                CLIENT_MESSAGE,
                "duplicate retry id"
        ));
        assertRejected(() -> insertMessage(
                id(303),
                CONVERSATION,
                USER_LOW,
                id(202),
                "   "
        ));
        assertRejected(() -> insertMessage(
                id(304),
                CONVERSATION,
                USER_LOW,
                id(203),
                "x".repeat(2001)
        ));
        assertRejected(() -> insertMessage(
                id(305),
                id(999),
                USER_LOW,
                id(204),
                "missing conversation"
        ));
        assertRejected(() -> insertMessage(
                id(306),
                CONVERSATION,
                OTHER_USER,
                id(205),
                "third party sender"
        ));
        assertRejected(() -> insertTypedMessage(
                id(309), CONVERSATION, USER_LOW, id(209),
                "TEXT", "mixed text", 21.0, null, null
        ));
        assertRejected(() -> insertTypedMessage(
                id(310), CONVERSATION, USER_LOW, id(210),
                "LOCATION", "mixed location", 21.0, 105.0, 10.0
        ));
        assertRejected(() -> insertTypedMessage(
                id(311), CONVERSATION, USER_LOW, id(211),
                "LOCATION", null, null, 105.0, 10.0
        ));
        assertRejected(() -> insertTypedMessage(
                id(312), CONVERSATION, USER_LOW, id(212),
                "LOCATION", null, 21.0, null, 10.0
        ));
        assertRejected(() -> insertTypedMessage(
                id(313), CONVERSATION, USER_LOW, id(213),
                "LOCATION", null, 21.0, 105.0, null
        ));
        assertRejected(() -> insertTypedMessage(
                id(314), CONVERSATION, USER_LOW, id(214),
                "LOCATION", null, 90.0001, 105.0, 10.0
        ));
        assertRejected(() -> insertTypedMessage(
                id(315), CONVERSATION, USER_LOW, id(215),
                "LOCATION", null, 21.0, -180.0001, 10.0
        ));
        assertRejected(() -> insertTypedMessage(
                id(316), CONVERSATION, USER_LOW, id(216),
                "LOCATION", null, 21.0, 105.0, -0.01
        ));
        assertRejected(() -> insertTypedMessage(
                id(317), CONVERSATION, USER_LOW, id(217),
                "LOCATION", null, 21.0, 105.0, 1_000_000.01
        ));
        assertRejected(() -> insertTypedMessage(
                id(318), CONVERSATION, USER_LOW, id(218),
                "VIDEO", null, null, null, null
        ));
        assertRejected(() -> insertTypedMessage(
                id(319), CONVERSATION, USER_LOW, id(219),
                "LOCATION", null, Double.NaN, 105.0, 10.0
        ));
        assertRejected(() -> insertTypedMessage(
                id(320), CONVERSATION, USER_LOW, id(220),
                "LOCATION", null, 21.0, Double.POSITIVE_INFINITY, 10.0
        ));
        assertRejected(() -> insertTypedMessage(
                id(321), CONVERSATION, USER_LOW, id(221),
                "LOCATION", null, 21.0, 105.0, Double.POSITIVE_INFINITY
        ));

        // A client-generated id is scoped to its sender within the conversation.
        insertMessage(id(307), CONVERSATION, USER_HIGH, CLIENT_MESSAGE, "reply");
        assertThat(count("chat_messages")).isEqualTo(5);

        jdbcTemplate.update("DELETE FROM users WHERE id = ?", USER_LOW);
        assertThat(count("chat_conversations")).isZero();
        assertThat(count("chat_messages")).isZero();
    }

    private void insertConversation(UUID id, UUID userLowId, UUID userHighId) {
        jdbcTemplate.update(
                """
                        INSERT INTO chat_conversations (
                            id, user_low_id, user_high_id, created_at, updated_at
                        ) VALUES (?, ?, ?, ?, ?)
                        """,
                id,
                userLowId,
                userHighId,
                Timestamp.from(NOW),
                Timestamp.from(NOW)
        );
    }

    private void insertMessage(
            UUID id,
            UUID conversationId,
            UUID senderId,
            UUID clientMessageId,
            String content
    ) {
        jdbcTemplate.update(
                """
                        INSERT INTO chat_messages (
                            id, conversation_id, sender_id, user_low_id, user_high_id,
                            client_message_id, content, created_at
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                id,
                conversationId,
                senderId,
                USER_LOW,
                USER_HIGH,
                clientMessageId,
                content,
                Timestamp.from(NOW)
        );
    }

    private void insertTypedMessage(
            UUID id,
            UUID conversationId,
            UUID senderId,
            UUID clientMessageId,
            String messageType,
            String content,
            Double latitude,
            Double longitude,
            Double accuracyMeters
    ) {
        jdbcTemplate.update(
                """
                        INSERT INTO chat_messages (
                            id, conversation_id, sender_id, user_low_id, user_high_id,
                            client_message_id, message_type, content, latitude, longitude,
                            accuracy_meters, created_at
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                id,
                conversationId,
                senderId,
                USER_LOW,
                USER_HIGH,
                clientMessageId,
                messageType,
                content,
                latitude,
                longitude,
                accuracyMeters,
                Timestamp.from(NOW)
        );
    }

    private int count(String table) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }

    private void assertRejected(org.assertj.core.api.ThrowableAssert.ThrowingCallable operation) {
        assertThatThrownBy(operation).isInstanceOf(DataIntegrityViolationException.class);
    }

    private static UUID id(long suffix) {
        return UUID.fromString("00000000-0000-0000-0000-" + String.format("%012d", suffix));
    }
}
