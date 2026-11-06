package com.eparly.api.catalogsync.scheduler;

import com.eparly.api.catalogsync.CatalogSyncProperties;
import com.eparly.api.catalogsync.domain.CatalogSyncReport;
import com.eparly.api.catalogsync.orchestration.CatalogSyncJobService;
import com.eparly.api.catalogsync.persistence.CatalogSyncRepository;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.UUID;

@Component
@ConditionalOnProperty(prefix = "eparly.catalog-sync", name = "enabled", havingValue = "true")
public class CatalogSyncScheduler {
    private static final Logger log = LoggerFactory.getLogger(CatalogSyncScheduler.class);
    private static final Duration CATCH_UP_AGE = Duration.ofHours(24);
    private static final Duration CATCH_UP_DELAY = Duration.ofMinutes(2);

    private final CatalogSyncJobService jobService;
    private final CatalogSyncRepository repository;
    private final CatalogSyncProperties properties;
    private final TaskScheduler taskScheduler;
    private final Clock clock;
    private final UUID instanceId = UUID.randomUUID();
    private volatile boolean registered;

    public CatalogSyncScheduler(CatalogSyncJobService jobService, CatalogSyncRepository repository,
                                CatalogSyncProperties properties, TaskScheduler taskScheduler, Clock clock) {
        this.jobService = jobService;
        this.repository = repository;
        this.properties = properties;
        this.taskScheduler = taskScheduler;
        this.clock = clock;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void announceAndCatchUp() {
        ZonedDateTime next = nextRun();
        repository.registerSchedulerInstance(instanceId, ProcessHandle.current().pid(), hostName(),
                properties.getCron(), properties.getZone(), properties.isBusinessIngestionEnabled(),
                next.toInstant(), clock.instant());
        registered = true;
        log.info("Catalog sync scheduler active: instance={} pid={} cron='{}' zone={} nextRun={} businessIngestion={}",
                instanceId, ProcessHandle.current().pid(), properties.getCron(), properties.getZone(), next,
                properties.isBusinessIngestionEnabled());
        if (properties.isCatchUpOnStartup() && !jobService.succeededSince(clock.instant().minus(CATCH_UP_AGE))) {
            log.info("Catalog sync catch-up scheduled: no successful run in the last {}", CATCH_UP_AGE);
            taskScheduler.schedule(() -> execute(false), clock.instant().plus(CATCH_UP_DELAY));
        }
    }

    @Scheduled(fixedDelayString = "PT5M", initialDelayString = "PT5M")
    public void heartbeat() {
        if (registered) repository.heartbeatSchedulerInstance(instanceId, nextRun().toInstant(), clock.instant());
    }

    @PreDestroy
    public void markStopped() {
        if (!registered) return;
        try {
            repository.stopSchedulerInstance(instanceId, clock.instant());
        } catch (RuntimeException exception) {
            log.warn("Catalog sync scheduler stop could not be recorded: {}", exception.getClass().getSimpleName());
        }
    }

    @Scheduled(cron = "${eparly.catalog-sync.cron:0 0 3 * * *}",
            zone = "${eparly.catalog-sync.zone:Europe/Paris}")
    public void executeNightly() {
        execute(true);
    }

    /** Une seule nouvelle tentative par cycle ; le verrou PostgreSQL reste le garde-fou de concurrence. */
    private void execute(boolean retryOnFailure) {
        boolean failed = false;
        try {
            CatalogSyncReport report = jobService.runNow("scheduler:" + instanceId);
            failed = "FAILED".equals(report.status());
            log.info("Catalog sync run {} finished with status {} in {} ms: sources={}, failed={}, skipped={}, "
                            + "notModified={}, pages={}, unsupportedPages={}, collected={}, new={}, updated={}, "
                            + "unchanged={}, duplicates={}, rejected={}, notReturned={}, errors={}, "
                            + "providersCreated={}, catalogOffersCreated={}, catalogPricesChanged={}, evidenceAdded={}",
                    report.id(), report.status(), report.durationMillis(), report.sourcesProcessed(),
                    report.sourcesFailed(), report.sourcesSkipped(), report.sourcesNotModified(),
                    report.pagesFetched(), report.pagesUnsupported(), report.offersCollected(),
                    report.offersDiscovered(), report.offersUpdated(), report.offersUnchanged(),
                    report.offersDuplicates(), report.offersRejected(), report.offersNotReturned(),
                    report.errorsCount(), report.providersCreated(), report.catalogOffersCreated(),
                    report.catalogPricesChanged(), report.catalogEvidenceAdded());
        } catch (RuntimeException exception) {
            failed = true;
            log.error("Catalog sync job failed before completing its run", exception);
        }
        if (failed && retryOnFailure) {
            Instant retryAt = clock.instant().plus(properties.getFailureRetryDelay());
            log.warn("Catalog sync retry scheduled at {}", retryAt);
            taskScheduler.schedule(() -> execute(false), retryAt);
        }
    }

    private ZonedDateTime nextRun() {
        ZoneId zone = ZoneId.of(properties.getZone());
        return CronExpression.parse(properties.getCron()).next(ZonedDateTime.now(clock.withZone(zone)));
    }

    private static String hostName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException exception) {
            return "unknown";
        }
    }
}
