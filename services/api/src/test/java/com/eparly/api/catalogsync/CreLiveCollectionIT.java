package com.eparly.api.catalogsync;

import com.eparly.api.TestJwtConfiguration;
import com.eparly.api.catalogsync.connector.CreElectricityTariffConnector;
import com.eparly.api.catalogsync.domain.CatalogSyncReport;
import com.eparly.api.catalogsync.orchestration.CatalogSyncJobService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lecture reelle des trois fichiers open data de la CRE, sur une base PostgreSQL jetable.
 * Ignore par defaut pour que la suite reste hors ligne ; lancement explicite avec
 * CATALOG_LIVE_CRE=true et -Dtest=CreLiveCollectionIT.
 */
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
@Import(TestJwtConfiguration.class)
@EnabledIfEnvironmentVariable(named = "CATALOG_LIVE_CRE", matches = "true")
class CreLiveCollectionIT {
    private static final String BASE = "https://www.cre.fr/fileadmin/Documents/Open_data/Marches_de_detail/";

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired private JdbcTemplate jdbc;
    @Autowired private CatalogSyncJobService jobService;

    @Test
    void collectsRealRegulatedTariffsIntoCandidatesOnly() {
        for (String file : List.of("Option_Base.csv", "Option_HPHC.csv", "Option_Tempo.csv")) {
            jdbc.update("""
                    INSERT INTO catalog_sync_sources
                        (source_name, official_domain, source_url, source_type, sector, country, collection_enabled,
                         permission_status, permission_reason, permission_updated_at, connector_key)
                    VALUES (?, 'cre.fr', ?, 'OFFICIAL_API', 'ELECTRICITY', 'FR', TRUE, 'AUTHORIZED',
                            'Verification ponctuelle sur base jetable', CURRENT_TIMESTAMP, ?)
                    """, "CRE " + file, BASE + file, CreElectricityTariffConnector.KEY);
        }

        CatalogSyncReport first = jobService.runNow();
        System.out.println("LIVE first run: " + first);
        jobService.runSources(first.id()).forEach(source -> System.out.println("LIVE source: " + source));
        jdbc.queryForList("""
                SELECT offer_external_key, normalized_data->>'amountMinor' AS amount, price_kind, comparability_status,
                       normalized_data->>'validFrom' AS valid_from
                FROM catalog_offer_observations ORDER BY offer_external_key LIMIT 6
                """).forEach(row -> System.out.println("LIVE observation: " + row));

        CatalogSyncReport second = jobService.runNow();
        System.out.println("LIVE second run: " + second);

        assertThat(first.status()).isEqualTo("SUCCESS");
        assertThat(first.offersDiscovered()).isPositive();
        assertThat(second.status()).isEqualTo("SUCCESS");
        assertThat(second.offersDiscovered()).isZero();
        assertThat(second.offersUpdated()).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM commercial_offers", Integer.class)).isZero();
    }
}
