package com.eparly.api.catalogsync.orchestration;

import com.eparly.api.catalogsync.CatalogSyncProperties;
import com.eparly.api.catalogsync.connector.CatalogConnectorRegistry;
import com.eparly.api.catalogsync.connector.GenericOfficialSourceConnector;
import com.eparly.api.catalogsync.connector.CatalogConnectorRegistry;
import com.eparly.api.catalogsync.connector.GenericOfficialSourceConnector;
import com.eparly.api.catalogsync.domain.CatalogSourceType;
import com.eparly.api.catalogsync.domain.CatalogSyncSource;
import com.eparly.api.catalogsync.discovery.SitemapDiscoveryService;
import com.eparly.api.catalogsync.extraction.HtmlCatalogOfferExtractor;
import com.eparly.api.catalogsync.extraction.JsonCatalogOfferExtractor;
import com.eparly.api.catalogsync.fetch.OfficialSourceHttpClient;
import com.eparly.api.catalogsync.fetch.RobotsPolicyService;
import com.eparly.api.catalogsync.normalization.CatalogDeduplicationService;
import com.eparly.api.catalogsync.normalization.CatalogDeduplicationService;
import com.eparly.api.catalogsync.normalization.OfferNormalizationService;
import com.eparly.api.catalogsync.persistence.CatalogSyncRepository;
import com.eparly.api.offer.domain.OfferSector;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CatalogSyncCoordinatorTest {
    private static CatalogSyncCoordinator coordinator(CatalogSyncRepository repository,
                                                      OfficialSourceHttpClient httpClient,
                                                      RobotsPolicyService robotsPolicy,
                                                      CatalogSyncProperties properties) {
        GenericOfficialSourceConnector generic = new GenericOfficialSourceConnector(httpClient, robotsPolicy,
                mock(SitemapDiscoveryService.class), mock(JsonCatalogOfferExtractor.class),
                mock(HtmlCatalogOfferExtractor.class), properties);
        return new CatalogSyncCoordinator(repository,
                mock(com.eparly.api.catalogsync.persistence.CatalogBusinessIngestionService.class),
                new CatalogConnectorRegistry(List.of(generic), generic),
                robotsPolicy, mock(OfferNormalizationService.class), mock(CatalogDeduplicationService.class),
                properties, Clock.systemUTC());
    }

    @Test
    void expiredRunDeadlinePreventsStartingSourceFetches() {
        CatalogSyncRepository repository = mock(CatalogSyncRepository.class);
        OfficialSourceHttpClient httpClient = mock(OfficialSourceHttpClient.class);
        RobotsPolicyService robotsPolicy = mock(RobotsPolicyService.class);
        CatalogSyncProperties properties = new CatalogSyncProperties();
        properties.setMaxExecutionDuration(Duration.ofNanos(1));
        CatalogSyncCoordinator coordinator = coordinator(repository, httpClient, robotsPolicy, properties);
        CatalogSyncSource source = new CatalogSyncSource(UUID.randomUUID(), null, "Fixture", "example.com",
                "https://example.com/offers", CatalogSourceType.JSON_FEED, OfferSector.STREAMING,
                true, "AUTHORIZED", null, null, "{}");
        var sourceErrors = new LinkedHashMap<String, String>();

        CatalogSyncRepository.SyncCounters counters = coordinator.collect(List.of(source), UUID.randomUUID(), sourceErrors);

        assertThat(counters.sourcesProcessed).isZero();
        assertThat(sourceErrors).containsEntry("run", "Maximum catalog sync execution duration reached");
        verify(robotsPolicy).beginRun();
        verify(robotsPolicy, never()).isAllowed(source, source.sourceUrl());
        verify(httpClient, never()).fetch(source, source.sourceUrl(), null, null);
    }

    @Test
    void csvFeedSourceIsProcessedWithoutAutomaticExtraction() {
        CatalogSyncRepository repository = mock(CatalogSyncRepository.class);
        OfficialSourceHttpClient httpClient = mock(OfficialSourceHttpClient.class);
        RobotsPolicyService robotsPolicy = mock(RobotsPolicyService.class);
        CatalogSyncProperties properties = new CatalogSyncProperties();
        properties.setMaxExecutionDuration(Duration.ofSeconds(60));
        
        CatalogSyncCoordinator coordinator = coordinator(repository, httpClient, robotsPolicy, properties);
        
        CatalogSyncSource source = new CatalogSyncSource(UUID.randomUUID(), null, "CSV source", "merchant.example.fr",
                "https://feed.example.com/telco", CatalogSourceType.CSV_FEED, OfferSector.MOBILE_PLAN,
                true, "AUTHORIZED", null, null, "{}");
        
        var sourceErrors = new LinkedHashMap<String, String>();
        
        coordinator.collect(List.of(source), UUID.randomUUID(), sourceErrors);
        
        verify(robotsPolicy).isAllowed(source, source.sourceUrl());
    }

    @Test
    void csvFeedWithNonMobileSectorThrowsException() {
        CatalogSyncRepository repository = mock(CatalogSyncRepository.class);
        OfficialSourceHttpClient httpClient = mock(OfficialSourceHttpClient.class);
        RobotsPolicyService robotsPolicy = mock(RobotsPolicyService.class);
        when(robotsPolicy.isAllowed(any(), any())).thenReturn(true);
        when(httpClient.fetch(any(), any(), any(), any())).thenReturn(
                new com.eparly.api.catalogsync.fetch.FetchResult(200, java.net.URI.create("https://feed.example.com"), 
                        "text/csv", "dummy", null, null));
        
        CatalogSyncProperties properties = new CatalogSyncProperties();
        properties.setMaxExecutionDuration(Duration.ofSeconds(60));
        
        CatalogSyncCoordinator coordinator = coordinator(repository, httpClient, robotsPolicy, properties);
        
        CatalogSyncSource source = new CatalogSyncSource(UUID.randomUUID(), null, "Test CSV", "example.fr",
                "https://feed.example.com", CatalogSourceType.CSV_FEED, OfferSector.STREAMING,
                true, "AUTHORIZED", null, null, "{}");
        
        var sourceErrors = new LinkedHashMap<String, String>();
        
        CatalogSyncRepository.SyncCounters counters = coordinator.collect(List.of(source), UUID.randomUUID(), sourceErrors);
        
        assertThat(counters.errorsCount).isEqualTo(1);
        assertThat(sourceErrors).containsKey(source.id().toString());
        assertThat(sourceErrors.get(source.id().toString())).contains("CSV_FEED automatic collection is disabled for V1");
    }
}
