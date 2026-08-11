package com.hadilao.be.modules.user.repository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.Sql;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:user_profile_migration;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.flyway.enabled=false"
})
@Sql(statements = {
        "CREATE TABLE users (id UUID PRIMARY KEY)",
        "INSERT INTO users (id) VALUES ('00000000-0000-0000-0000-000000000001')"
})
@Sql(scripts = "classpath:db/migration/V9__add_user_profile_fields.sql")
class UserProfileMigrationTest {

    private static final UUID EXISTING_USER =
            UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void migrationBackfillsExistingUsersAndEnforcesProfileConstraints() {
        assertThat(jdbcTemplate.queryForObject(
                "SELECT gender FROM users WHERE id = ?", String.class, EXISTING_USER))
                .isEqualTo("UNSPECIFIED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT birth_year FROM users WHERE id = ?", Integer.class, EXISTING_USER))
                .isNull();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT address FROM users WHERE id = ?", String.class, EXISTING_USER))
                .isNull();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT avatar_public_id FROM users WHERE id = ?", String.class, EXISTING_USER))
                .isNull();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT row_version FROM users WHERE id = ?", Long.class, EXISTING_USER))
                .isZero();

        jdbcTemplate.update(
                "UPDATE users SET gender = ?, birth_year = ?, address = ?, avatar_public_id = ? WHERE id = ?",
                "FEMALE", 1995, "Ha Noi", "linkcute/avatars/profile", EXISTING_USER);

        assertThat(jdbcTemplate.queryForObject(
                "SELECT gender FROM users WHERE id = ?", String.class, EXISTING_USER))
                .isEqualTo("FEMALE");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT birth_year FROM users WHERE id = ?", Integer.class, EXISTING_USER))
                .isEqualTo(1995);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT address FROM users WHERE id = ?", String.class, EXISTING_USER))
                .isEqualTo("Ha Noi");

        assertRejected(() -> jdbcTemplate.update(
                "UPDATE users SET gender = 'UNKNOWN' WHERE id = ?", EXISTING_USER));
        assertRejected(() -> jdbcTemplate.update(
                "UPDATE users SET birth_year = 1899 WHERE id = ?", EXISTING_USER));
        assertRejected(() -> jdbcTemplate.update(
                "UPDATE users SET birth_year = 2101 WHERE id = ?", EXISTING_USER));
        assertRejected(() -> jdbcTemplate.update(
                "UPDATE users SET address = ? WHERE id = ?", "x".repeat(256), EXISTING_USER));
        assertRejected(() -> jdbcTemplate.update(
                "UPDATE users SET row_version = -1 WHERE id = ?", EXISTING_USER));
    }

    private void assertRejected(org.assertj.core.api.ThrowableAssert.ThrowingCallable operation) {
        assertThatThrownBy(operation).isInstanceOf(DataIntegrityViolationException.class);
    }
}
