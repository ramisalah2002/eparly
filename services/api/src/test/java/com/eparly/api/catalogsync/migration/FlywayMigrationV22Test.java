package com.eparly.api.catalogsync.migration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FlywayMigrationV22Test {

    @Test
    void preservesExistingCatalogRowsAndOnlyWidensAllowedValues() throws SQLException {
        try (PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine")) {
            postgres.start();
            Flyway v21 = Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(),
                    postgres.getPassword()).locations("classpath:db/migration").target("21").load();
            v21.migrate();
            try (Connection connection = v21.getConfiguration().getDataSource().getConnection();
                 Statement statement = connection.createStatement()) {
                statement.executeUpdate("""
                        INSERT INTO offer_providers (id, name, sector, official_website)
                        VALUES ('00000000-0000-0000-0000-000000000001', 'Existant', 'MOBILE_PLAN', 'https://a.example.fr')
                        """);
                statement.executeUpdate("""
                        INSERT INTO commercial_offers (id, provider_id, commercial_name, sector)
                        VALUES ('00000000-0000-0000-0000-000000000002',
                                '00000000-0000-0000-0000-000000000001', 'Offre existante', 'MOBILE_PLAN')
                        """);
                statement.executeUpdate("""
                        INSERT INTO catalog_sync_runs (id, status)
                        VALUES ('00000000-0000-0000-0000-000000000003', 'SUCCEEDED')
                        """);
            }

            Flyway latest = Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(),
                    postgres.getPassword()).locations("classpath:db/migration").target("22").load();
            latest.migrate();
            latest.validate();
            assertThat(latest.info().current().getVersion().toString()).isEqualTo("22");

            try (Connection connection = latest.getConfiguration().getDataSource().getConnection();
                 Statement statement = connection.createStatement()) {
                try (ResultSet rows = statement.executeQuery("""
                        SELECT publication_status, sector FROM commercial_offers
                        WHERE id = '00000000-0000-0000-0000-000000000002'
                        """)) {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getString("publication_status")).isEqualTo("DRAFT");
                    assertThat(rows.getString("sector")).isEqualTo("MOBILE_PLAN");
                }
                try (ResultSet rows = statement.executeQuery(
                        "SELECT status, offers_duplicates FROM catalog_sync_runs")) {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getString("status")).isEqualTo("SUCCEEDED");
                    assertThat(rows.getInt("offers_duplicates")).isZero();
                }
                statement.executeUpdate("""
                        INSERT INTO offer_providers (name, sector, official_website)
                        VALUES ('Energie', 'ELECTRICITY', 'https://b.example.fr')
                        """);
                statement.executeUpdate("INSERT INTO catalog_sync_runs (status) VALUES ('PARTIAL_SUCCESS')");
                assertThatThrownBy(() -> statement.executeUpdate("""
                        INSERT INTO offer_providers (name, sector, official_website)
                        VALUES ('Invalide', 'NOT_A_SECTOR', 'https://c.example.fr')
                        """)).isInstanceOf(SQLException.class);
            }
        }
    }
}
