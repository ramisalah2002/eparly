package com.eparly.api.catalogsync;

import com.eparly.api.catalogsync.domain.CatalogSourceType;
import com.eparly.api.catalogsync.domain.CatalogSyncReport;
import com.eparly.api.catalogsync.domain.CatalogSyncSource;
import com.eparly.api.catalogsync.fetch.FetchResult;
import com.eparly.api.catalogsync.fetch.OfficialSourceHttpClient;
import com.eparly.api.catalogsync.fetch.RobotsPolicyService;
import com.eparly.api.catalogsync.normalization.CatalogCategorySpecs;
import com.eparly.api.catalogsync.normalization.CatalogComparability;
import com.eparly.api.catalogsync.orchestration.CatalogSyncJobService;
import com.eparly.api.catalogsync.persistence.CatalogSyncRepository;
import com.eparly.api.catalogsync.scheduler.CatalogSyncScheduler;
import com.eparly.api.offer.domain.OfferSector;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.TaskScheduler;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CatalogBatchRobustnessTest {
    /** Forme reelle du robots.txt Opendatasoft : l'autorisation d'/api/ ne vaut que pour Googlebot. */
    private static final String ODS_ROBOTS = """
            User-agent: Twitterbot
            Disallow:

            User-agent: *
            Disallow: /logout
            Disallow: /api/

            User-agent: Googlebot
            Allow: /api/
            Disallow: /api/explore/v2*/exports/
            """;

    @Test
    void googlebotOnlyAllowanceDoesNotApplyToOurRobot() {
        RobotsPolicyService robots = robots(ODS_ROBOTS);

        assertThat(robots.isAllowed(source(), "https://data.example.fr/api/explore/v2.1/catalog/datasets/x/records"))
                .isFalse();
        assertThat(robots.isAllowed(source(), "https://data.example.fr/explore/dataset/x")).isTrue();
    }

    @Test
    void longestRuleWinsAndAllowWinsTies() {
        RobotsPolicyService robots = robots("""
                User-agent: *
                Disallow: /private/
                Allow: /private/public/
                Disallow: /tie/
                Allow: /tie/
                """);

        assertThat(robots.isAllowed(source(), "https://data.example.fr/private/x")).isFalse();
        assertThat(robots.isAllowed(source(), "https://data.example.fr/private/public/x")).isTrue();
        assertThat(robots.isAllowed(source(), "https://data.example.fr/tie/x")).isTrue();
    }

    @Test
    void referenceTariffIsNeverComparable() {
        CatalogComparability result = new CatalogCategorySpecs().evaluate(OfferSector.ELECTRICITY,
                Map.of("offerNature", "REFERENCE_REGULATED_TARIFF", "tariffOption", "BASE", "powerKva", "6",
                        "zone", "FR", "kwhPricingKnown", true));

        assertThat(result.status()).isEqualTo(CatalogComparability.Status.NOT_COMPARABLE);
        assertThat(result.comparisonGroup()).isNull();
    }

    @Test
    void failedScheduledRunIsRetriedExactlyOnce() {
        CatalogSyncJobService jobs = mock(CatalogSyncJobService.class);
        TaskScheduler tasks = mock(TaskScheduler.class);
        when(jobs.runNow(anyString())).thenReturn(report("FAILED"));
        CatalogSyncScheduler scheduler = new CatalogSyncScheduler(jobs, mock(CatalogSyncRepository.class),
                new CatalogSyncProperties(), tasks, Clock.systemUTC());

        scheduler.executeNightly();

        var retry = org.mockito.ArgumentCaptor.forClass(Runnable.class);
        verify(tasks, times(1)).schedule(retry.capture(), any(Instant.class));
        retry.getValue().run();
        verify(jobs, times(2)).runNow(anyString());
        verify(tasks, times(1)).schedule(any(Runnable.class), any(Instant.class));
    }

    @Test
    void successfulOrSkippedRunIsNotRetried() {
        CatalogSyncJobService jobs = mock(CatalogSyncJobService.class);
        TaskScheduler tasks = mock(TaskScheduler.class);
        when(jobs.runNow(anyString())).thenReturn(report("SUCCESS"), report("SKIPPED"));
        CatalogSyncScheduler scheduler = new CatalogSyncScheduler(jobs, mock(CatalogSyncRepository.class),
                new CatalogSyncProperties(), tasks, Clock.systemUTC());

        scheduler.executeNightly();
        scheduler.executeNightly();

        verify(tasks, never()).schedule(any(Runnable.class), any(Instant.class));
    }

    private static RobotsPolicyService robots(String content) {
        OfficialSourceHttpClient http = mock(OfficialSourceHttpClient.class);
        when(http.fetch(any(), any(), any(), any())).thenReturn(new FetchResult(200,
                URI.create("https://data.example.fr/robots.txt"), "text/plain", content, null, null));
        return new RobotsPolicyService(http);
    }

    private static CatalogSyncSource source() {
        return new CatalogSyncSource(UUID.randomUUID(), null, "Test", "example.fr", "https://data.example.fr/a",
                CatalogSourceType.JSON_FEED, OfferSector.TRANSPORT, true, "AUTHORIZED", null, null, "{}");
    }

    private static CatalogSyncReport report(String status) {
        Instant now = Instant.now();
        return new CatalogSyncReport(UUID.randomUUID(), now, now, status, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0L,
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
    }
}
