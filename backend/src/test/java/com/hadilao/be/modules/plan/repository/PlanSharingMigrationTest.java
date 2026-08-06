package com.hadilao.be.modules.plan.repository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.Sql;

import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:plan_sharing_migration;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.flyway.enabled=false"
})
@Sql(statements = {
        "CREATE TABLE users (id UUID PRIMARY KEY)",
        "CREATE TABLE places (id UUID PRIMARY KEY)"
})
@Sql(scripts = "classpath:db/migration/V8__create_plan_sharing.sql")
class PlanSharingMigrationTest {

    private static final UUID OWNER = id(1);
    private static final UUID SECOND_OWNER = id(2);
    private static final UUID INVITEE = id(3);
    private static final UUID OTHER_USER = id(4);
    private static final UUID THIRD_USER = id(5);

    private static final UUID PLAN = id(101);
    private static final UUID SECOND_PLAN = id(102);
    private static final UUID ITEM = id(201);
    private static final UUID INVITATION = id(301);
    private static final UUID MEMBER = id(401);

    private static final List<UUID> PLACES = List.of(
            id(501), id(502), id(503), id(504), id(505), id(506)
    );

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void migrationEnforcesSharingInvariantsAndReferentialActions() {
        List.of(OWNER, SECOND_OWNER, INVITEE, OTHER_USER, THIRD_USER)
                .forEach(userId -> jdbcTemplate.update("INSERT INTO users (id) VALUES (?)", userId));
        PLACES.forEach(placeId -> jdbcTemplate.update("INSERT INTO places (id) VALUES (?)", placeId));

        insertPlan(PLAN, OWNER, "plan-client-1", 0);
        insertPlan(SECOND_PLAN, SECOND_OWNER, "plan-client-1", 0);
        insertItem(ITEM, PLAN, "item-client-1", 0, PLACES.get(0), 10, 0, 11, 0,
                21.03, 105.85, 50_000.0, 100_000.0);
        insertInvitation(INVITATION, PLAN, OWNER, INVITEE, "ACCEPTED", now());
        insertMember(MEMBER, PLAN, INVITEE, INVITATION);

        assertThat(count("plans")).isEqualTo(2);
        assertThat(count("plan_items")).isOne();
        assertThat(count("plan_invitations")).isOne();
        assertThat(count("plan_members")).isOne();

        // The same client-generated id is allowed for different owners, but not twice per owner.
        assertRejected(() -> insertPlan(id(103), OWNER, "plan-client-1", 0));
        assertRejected(() -> insertPlan(id(104), OWNER, "negative-version", -1));

        assertRejected(() -> insertItem(id(202), PLAN, "negative-position", -1, PLACES.get(1),
                10, 0, 11, 0, 21.03, 105.85, null, null));
        assertRejected(() -> insertItem(id(203), PLAN, "invalid-time", 1, PLACES.get(2),
                12, 0, 11, 0, 21.03, 105.85, null, null));
        assertRejected(() -> insertItem(id(204), PLAN, "invalid-latitude", 1, PLACES.get(3),
                null, null, null, null, 91.0, 105.85, null, null));
        assertRejected(() -> insertItem(id(205), PLAN, "invalid-price", 1, PLACES.get(4),
                null, null, null, null, 21.03, 105.85, 200_000.0, 100_000.0));
        assertRejected(() -> insertItem(id(206), PLAN, "item-client-1", 1, PLACES.get(5),
                null, null, null, null, 21.03, 105.85, null, null));
        assertRejected(() -> insertItem(id(207), PLAN, "another-client-item", 1, PLACES.get(0),
                null, null, null, null, 21.03, 105.85, null, null));

        // Legacy local plans may have a place snapshot without coordinates.
        insertItem(id(208), SECOND_PLAN, "legacy-item", 0, PLACES.get(5),
                null, null, null, null, null, null, null, null);
        assertThat(countWhere("plan_items", "plan_id", SECOND_PLAN)).isOne();

        assertRejected(() -> insertInvitation(id(302), PLAN, OWNER, OWNER, "PENDING", null));
        assertRejected(() -> insertInvitation(id(303), PLAN, OWNER, OTHER_USER, "UNKNOWN", now()));
        assertRejected(() -> insertInvitation(id(304), PLAN, OWNER, OTHER_USER, "PENDING", now()));
        assertRejected(() -> insertInvitation(id(305), PLAN, OWNER, THIRD_USER, "ACCEPTED", null));
        assertRejected(() -> insertInvitation(id(306), PLAN, OWNER, INVITEE, "PENDING", null));

        assertRejected(() -> insertMember(id(402), PLAN, INVITEE, null));
        assertRejected(() -> insertMember(id(403), PLAN, OTHER_USER, INVITATION));

        // A referenced place cannot disappear while its immutable plan snapshot still points to it.
        assertRejected(() -> jdbcTemplate.update("DELETE FROM places WHERE id = ?", PLACES.get(0)));

        // Invitation history may be removed independently without deleting an accepted member.
        jdbcTemplate.update("DELETE FROM plan_invitations WHERE id = ?", INVITATION);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT invitation_id FROM plan_members WHERE id = ?",
                UUID.class,
                MEMBER
        )).isNull();

        UUID pendingInvitation = id(307);
        insertInvitation(pendingInvitation, PLAN, OWNER, OTHER_USER, "PENDING", null);

        // Deleting a plan removes all of its items, invitations, and members.
        jdbcTemplate.update("DELETE FROM plans WHERE id = ?", PLAN);
        assertThat(countWhere("plan_items", "plan_id", PLAN)).isZero();
        assertThat(countWhere("plan_invitations", "plan_id", PLAN)).isZero();
        assertThat(countWhere("plan_members", "plan_id", PLAN)).isZero();

        // Deleting the owner removes plans owned by that account.
        jdbcTemplate.update("DELETE FROM users WHERE id = ?", SECOND_OWNER);
        assertThat(countWhere("plans", "id", SECOND_PLAN)).isZero();
    }

    private void insertPlan(UUID planId, UUID ownerId, String clientPlanId, long version) {
        jdbcTemplate.update(
                """
                        INSERT INTO plans (
                            id, owner_id, client_plan_id, name, planned_date,
                            client_updated_at, version
                        ) VALUES (?, ?, ?, ?, ?, ?, ?)
                        """,
                planId,
                ownerId,
                clientPlanId,
                "Weekend in Hanoi",
                Date.valueOf(LocalDate.of(2026, 8, 8)),
                now(),
                version
        );
    }

    private void insertItem(
            UUID itemId,
            UUID planId,
            String clientItemId,
            int position,
            UUID placeId,
            Integer startHour,
            Integer startMinute,
            Integer endHour,
            Integer endMinute,
            Double latitude,
            Double longitude,
            Double priceMin,
            Double priceMax
    ) {
        jdbcTemplate.update(
                """
                        INSERT INTO plan_items (
                            id, plan_id, client_item_id, position, place_id,
                            place_name, place_category, place_lat, place_lng,
                            place_price_min, place_price_max,
                            start_time, end_time
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                itemId,
                planId,
                clientItemId,
                position,
                placeId,
                "Hoan Kiem Lake",
                "ENTERTAINMENT",
                latitude,
                longitude,
                priceMin,
                priceMax,
                time(startHour, startMinute),
                time(endHour, endMinute)
        );
    }

    private void insertInvitation(
            UUID invitationId,
            UUID planId,
            UUID inviterId,
            UUID inviteeId,
            String status,
            Timestamp respondedAt
    ) {
        jdbcTemplate.update(
                """
                        INSERT INTO plan_invitations (
                            id, plan_id, inviter_id, invitee_id, status, responded_at
                        ) VALUES (?, ?, ?, ?, ?, ?)
                        """,
                invitationId,
                planId,
                inviterId,
                inviteeId,
                status,
                respondedAt
        );
    }

    private void insertMember(
            UUID memberId,
            UUID planId,
            UUID userId,
            UUID invitationId
    ) {
        jdbcTemplate.update(
                """
                        INSERT INTO plan_members (id, plan_id, user_id, invitation_id)
                        VALUES (?, ?, ?, ?)
                        """,
                memberId,
                planId,
                userId,
                invitationId
        );
    }

    private int count(String table) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }

    private int countWhere(String table, String column, UUID value) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + table + " WHERE " + column + " = ?",
                Integer.class,
                value
        );
    }

    private void assertRejected(org.assertj.core.api.ThrowableAssert.ThrowingCallable operation) {
        assertThatThrownBy(operation).isInstanceOf(DataIntegrityViolationException.class);
    }

    private static Timestamp now() {
        return Timestamp.from(Instant.parse("2026-08-05T08:15:30Z"));
    }

    private static Time time(Integer hour, Integer minute) {
        return hour == null || minute == null ? null : Time.valueOf(LocalTime.of(hour, minute));
    }

    private static UUID id(long suffix) {
        return UUID.fromString("00000000-0000-0000-0000-" + String.format("%012d", suffix));
    }
}
