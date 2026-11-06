package com.eparly.api.catalogsync.migration;

import liquibase.Scope;
import liquibase.command.CommandScope;
import liquibase.command.core.UpdateCommandStep;
import liquibase.command.core.UpdateCountCommandStep;
import liquibase.command.core.helpers.DatabaseChangelogCommandStep;
import liquibase.command.core.helpers.DbUrlConnectionArgumentsCommandStep;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests du changelog Liquibase qui reprend les migrations Flyway V1 à V21.
 *
 * Vérifie :
 * A. une base vierge reçoit V1 à V21 ;
 * B. une base arrêtée à V20 reçoit ensuite V21 (contrainte CSV_FEED) ;
 * C. une base déjà migrée par Flyway jusqu'à V20 n'est pas rejouée et reçoit seulement V21 ;
 * D. relancer la migration n'applique rien de plus.
 */
@Testcontainers
class LiquibaseMigrationTest {

    private static final String CHANGELOG = "db/changelog/db.changelog-master.yaml";
    private static final String CSV_FEED_INSERT =
            "INSERT INTO catalog_sync_sources (id, provider_id, source_name, official_domain, " +
                    "source_url, source_type, sector, country, collection_enabled, permission_status) " +
                    "VALUES ('%s', null, 'Test CSV', 'example.com', " +
                    "'https://example.com/feed.csv', 'CSV_FEED', 'MOBILE_PLAN', 'FR', false, 'PENDING') " +
                    "RETURNING source_type";

    @Test
    void scenarioA_freshDatabaseWithV1ToV21_appliesSuccessfully() throws Exception {
        try (PostgreSQLContainer<?> postgres = newContainer()) {
            postgres.start();

            update(postgres, null);

            assertThat(changesets(postgres, "EXECUTED")).hasSize(21).last().isEqualTo("V21__add_csv_feed_source_type");
            assertCsvFeedAccepted(postgres, "00000000-0000-0000-0000-000000000000");
        }
    }

    @Test
    void scenarioB_databaseAtV20ThenV21_appliesSuccessfully() throws Exception {
        try (PostgreSQLContainer<?> postgres = newContainer()) {
            postgres.start();

            update(postgres, 20);
            assertThat(changesets(postgres, "EXECUTED")).hasSize(20).last().isEqualTo("V20__catalog_sync_observations");
            assertCsvFeedRejected(postgres);

            update(postgres, null);
            assertThat(changesets(postgres, "EXECUTED")).hasSize(21);
            assertCsvFeedAccepted(postgres, "20000000-0000-0000-0000-000000000000");
        }
    }

    @Test
    void scenarioC_databaseMigratedByFlyway_isNotReplayed() throws Exception {
        try (PostgreSQLContainer<?> postgres = newContainer()) {
            postgres.start();
            simulateFlywayUpTo(postgres, 20);
            assertCsvFeedRejected(postgres);

            update(postgres, null);

            assertThat(changesets(postgres, "MARK_RAN")).hasSize(20).first().isEqualTo("V1__init_schema");
            assertThat(changesets(postgres, "EXECUTED")).containsExactly("V21__add_csv_feed_source_type");
            assertCsvFeedAccepted(postgres, "30000000-0000-0000-0000-000000000000");
        }
    }

    @Test
    void scenarioD_rerunAppliesNothing() throws Exception {
        try (PostgreSQLContainer<?> postgres = newContainer()) {
            postgres.start();

            update(postgres, null);
            update(postgres, null);

            assertThat(changesets(postgres, "EXECUTED")).hasSize(21);
            assertThat(changesets(postgres, "MARK_RAN")).isEmpty();
        }
    }

    private static PostgreSQLContainer<?> newContainer() {
        return new PostgreSQLContainer<>("postgres:17-alpine")
                .withDatabaseName("testdb")
                .withUsername("testuser")
                .withPassword("testpass");
    }

    private static Connection connect(PostgreSQLContainer<?> postgres) throws SQLException {
        return DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    }

    /** Applique le changelog complet, ou seulement les {@code count} premiers changesets. */
    private static void update(PostgreSQLContainer<?> postgres, Integer count) throws Exception {
        try (Connection connection = connect(postgres)) {
            Database database = DatabaseFactory.getInstance()
                    .findCorrectDatabaseImplementation(new JdbcConnection(connection));
            CommandScope command = count == null
                    ? new CommandScope(UpdateCommandStep.COMMAND_NAME)
                    : new CommandScope(UpdateCountCommandStep.COMMAND_NAME)
                            .addArgumentValue(UpdateCountCommandStep.COUNT_ARG, count);
            command.addArgumentValue(DbUrlConnectionArgumentsCommandStep.DATABASE_ARG, database)
                    .addArgumentValue(DatabaseChangelogCommandStep.CHANGELOG_FILE_ARG, CHANGELOG);
            Scope.child(Map.of(Scope.Attr.resourceAccessor.name(), new ClassLoaderResourceAccessor()), () -> {
                command.execute();
            });
        }
    }

    /** Reproduit une base migrée par l'ancienne configuration Flyway (scripts + historique). */
    private static void simulateFlywayUpTo(PostgreSQLContainer<?> postgres, int lastVersion) throws Exception {
        try (Connection connection = connect(postgres); Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE flyway_schema_history (version VARCHAR(50), success BOOLEAN NOT NULL)");
            for (int version = 1; version <= lastVersion; version++) {
                statement.execute(readScript(version));
                statement.execute("INSERT INTO flyway_schema_history VALUES ('" + version + "', true)");
            }
        }
    }

    private static String readScript(int version) throws IOException {
        Resource[] resources = new PathMatchingResourcePatternResolver().getResources("classpath:db/changelog/sql/V" + version + "__*.sql");
        assertThat(resources).hasSize(1);
        try (InputStream in = resources[0].getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static List<String> changesets(PostgreSQLContainer<?> postgres, String execType) throws SQLException {
        List<String> ids = new ArrayList<>();
        try (Connection connection = connect(postgres);
             var statement = connection.prepareStatement(
                     "SELECT id FROM databasechangelog WHERE exectype = ? ORDER BY orderexecuted")) {
            statement.setString(1, execType);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    ids.add(rs.getString(1));
                }
            }
        }
        return ids;
    }

    private static void assertCsvFeedAccepted(PostgreSQLContainer<?> postgres, String id) throws SQLException {
        try (Connection connection = connect(postgres);
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(CSV_FEED_INSERT.formatted(id))) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getString("source_type")).isEqualTo("CSV_FEED");
        }
    }

    private static void assertCsvFeedRejected(PostgreSQLContainer<?> postgres) throws SQLException {
        try (Connection connection = connect(postgres); Statement statement = connection.createStatement()) {
            assertThatThrownBy(() -> statement.executeQuery(
                    CSV_FEED_INSERT.formatted("10000000-0000-0000-0000-000000000000")))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("chk_catalog_sync_source_type");
        }
    }
}
