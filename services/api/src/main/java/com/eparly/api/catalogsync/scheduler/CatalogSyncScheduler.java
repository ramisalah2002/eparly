package com.eparly.api.catalogsync.scheduler;

import com.eparly.api.catalogsync.orchestration.CatalogSyncJobService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "eparly.catalog-sync", name = "enabled", havingValue = "true")
public class CatalogSyncScheduler {
    private static final Logger log = LoggerFactory.getLogger(CatalogSyncScheduler.class);
    private final CatalogSyncJobService jobService;

    public CatalogSyncScheduler(CatalogSyncJobService jobService) {
        this.jobService = jobService;
    }

    @Scheduled(cron = "${eparly.catalog-sync.cron:0 0 4 * * *}",
            zone = "${eparly.catalog-sync.zone:Europe/Paris}")
    public void executeNightly() {
        try {
            var report = jobService.runNow();
            log.info("Catalog sync run {} finished with status {}: sources={}, pages={}, unsupportedPages={}, "
                            + "offers={}, changed={}, errors={}",
                    report.id(), report.status(), report.sourcesProcessed(), report.pagesFetched(),
                    report.pagesUnsupported(), report.offersDiscovered(), report.offersUpdated(),
                    report.errorsCount());
        } catch (RuntimeException exception) {
            log.error("Catalog sync nightly job failed before completing its run", exception);
        }
    }
}
