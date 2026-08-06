package com.hadilao.be.modules.friendship.repository;

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
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:friendship_migration;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.flyway.enabled=false"
})
@Sql(statements = "CREATE TABLE users (id UUID PRIMARY KEY)")
@Sql(scripts = "classpath:db/migration/V7__create_friendships_table.sql")
class FriendshipMigrationTest {

    private static final UUID USER_1 = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID USER_2 = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID USER_3 = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final UUID USER_4 = UUID.fromString("00000000-0000-0000-0000-000000000004");
    private static final UUID USER_5 = UUID.fromString("00000000-0000-0000-0000-000000000005");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void migrationEnforcesTheFriendshipInvariants() {
        List.of(USER_1, USER_2, USER_3, USER_4, USER_5)
                .forEach(id -> jdbcTemplate.update("INSERT INTO users (id) VALUES (?)", id));

        insertFriendship(USER_1, USER_2, USER_1, "PENDING", null);
        insertFriendship(USER_1, USER_3, USER_3, "ACCEPTED", Timestamp.from(Instant.now()));

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM friendships", Integer.class))
                .isEqualTo(2);

        assertRejected(() -> insertFriendship(USER_1, USER_2, USER_2, "PENDING", null));
        assertRejected(() -> insertFriendship(USER_4, USER_3, USER_3, "PENDING", null));
        assertRejected(() -> insertFriendship(USER_5, USER_5, USER_5, "PENDING", null));
        assertRejected(() -> insertFriendship(USER_2, USER_3, USER_4, "PENDING", null));
        assertRejected(() -> insertFriendship(USER_2, USER_4, USER_2, "BLOCKED", null));
        assertRejected(() -> insertFriendship(
                USER_3,
                USER_5,
                USER_3,
                "PENDING",
                Timestamp.from(Instant.now())
        ));
        assertRejected(() -> insertFriendship(USER_4, USER_5, USER_4, "ACCEPTED", null));
    }

    private void insertFriendship(
            UUID userLowId,
            UUID userHighId,
            UUID requesterId,
            String status,
            Timestamp acceptedAt
    ) {
        jdbcTemplate.update(
                """
                        INSERT INTO friendships (
                            id, user_low_id, user_high_id, requester_id, status, accepted_at
                        ) VALUES (?, ?, ?, ?, ?, ?)
                        """,
                UUID.randomUUID(),
                userLowId,
                userHighId,
                requesterId,
                status,
                acceptedAt
        );
    }

    private void assertRejected(org.assertj.core.api.ThrowableAssert.ThrowingCallable operation) {
        assertThatThrownBy(operation).isInstanceOf(DataIntegrityViolationException.class);
    }
}
