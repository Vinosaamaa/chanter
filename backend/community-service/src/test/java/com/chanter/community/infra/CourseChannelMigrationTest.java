package com.chanter.community.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;

class CourseChannelMigrationTest {

    @Test
    void upgradesLegacySingleCohortDataWithoutRetainingCourseWideNameUniqueness() throws Exception {
        String databaseUrl = databaseUrl();
        migrateToVersionEleven(databaseUrl);
        LegacyFixture fixture = seedLegacyCourse(databaseUrl, 1);

        migrateToLatest(databaseUrl);

        try (Connection connection = DriverManager.getConnection(databaseUrl, "sa", "");
                Statement statement = connection.createStatement()) {
            try (ResultSet resultSet = statement.executeQuery("""
                        SELECT cohort_id
                        FROM course_channels
                        WHERE id = '%s'
                        """.formatted(fixture.channelId()))) {
                assertThat(resultSet.next()).isTrue();
                assertThat(resultSet.getObject(1, UUID.class)).isEqualTo(fixture.cohortId());
            }

            UUID secondCohortId = UUID.randomUUID();
            statement.executeUpdate("""
                    INSERT INTO cohorts (id, course_id, name, invite_code)
                    VALUES ('%s', '%s', 'Second Cohort', '%s')
                    """.formatted(secondCohortId, fixture.courseId(), UUID.randomUUID()));
            statement.executeUpdate("""
                    INSERT INTO course_channels (id, course_id, cohort_id, name, kind, position)
                    VALUES ('%s', '%s', '%s', 'announcements', 'TEXT', 0)
                    """.formatted(UUID.randomUUID(), fixture.courseId(), secondCohortId));
        }
    }

    @Test
    void refusesToGuessWhenLegacyCourseHasMultipleCohorts() throws Exception {
        String databaseUrl = databaseUrl();
        migrateToVersionEleven(databaseUrl);
        seedLegacyCourse(databaseUrl, 2);

        assertThatThrownBy(() -> migrateToLatest(databaseUrl))
                .isInstanceOf(FlywayException.class)
                .hasMessageContaining("V12__scope_course_channels_to_cohorts.sql");
    }

    private static String databaseUrl() {
        return "jdbc:h2:mem:course-channel-migration-" + UUID.randomUUID()
                + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1";
    }

    private static void migrateToVersionEleven(String databaseUrl) {
        Flyway.configure()
                .dataSource(databaseUrl, "sa", "")
                .target(MigrationVersion.fromVersion("11"))
                .load()
                .migrate();
    }

    private static void migrateToLatest(String databaseUrl) {
        Flyway.configure()
                .dataSource(databaseUrl, "sa", "")
                .load()
                .migrate();
    }

    private static LegacyFixture seedLegacyCourse(String databaseUrl, int cohortCount) throws SQLException {
        UUID ownerUserId = UUID.randomUUID();
        UUID studyServerId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        UUID firstCohortId = UUID.randomUUID();
        UUID channelId = UUID.randomUUID();
        try (Connection connection = DriverManager.getConnection(databaseUrl, "sa", "");
                Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    INSERT INTO study_servers (id, name, owner_user_id, created_at, plan_tier)
                    VALUES ('%s', 'Migration Study Server', '%s', CURRENT_TIMESTAMP, 'STARTER')
                    """.formatted(studyServerId, ownerUserId));
            statement.executeUpdate("""
                    INSERT INTO courses (id, study_server_id, title, instructor_user_id, created_at)
                    VALUES ('%s', '%s', 'Migration Course', '%s', CURRENT_TIMESTAMP)
                    """.formatted(courseId, studyServerId, ownerUserId));
            statement.executeUpdate("""
                    INSERT INTO cohorts (id, course_id, name, invite_code)
                    VALUES ('%s', '%s', 'Original Cohort', '%s')
                    """.formatted(firstCohortId, courseId, UUID.randomUUID()));
            if (cohortCount > 1) {
                statement.executeUpdate("""
                        INSERT INTO cohorts (id, course_id, name, invite_code)
                        VALUES ('%s', '%s', 'Ambiguous Cohort', '%s')
                        """.formatted(UUID.randomUUID(), courseId, UUID.randomUUID()));
            }
            statement.executeUpdate("""
                    INSERT INTO course_channels (id, course_id, name, kind, position)
                    VALUES ('%s', '%s', 'announcements', 'TEXT', 0)
                    """.formatted(channelId, courseId));
        }
        return new LegacyFixture(courseId, firstCohortId, channelId);
    }

    private record LegacyFixture(UUID courseId, UUID cohortId, UUID channelId) {
    }
}
