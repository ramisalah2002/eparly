package com.eparly.api.catalogsync.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Arret propre sous Windows, ou un processus sans console ne recoit pas de signal : le lanceur local depose un
 * fichier, le contexte Spring se ferme normalement (verrou PostgreSQL libere, instance marquee arretee).
 */
@Component
@ConditionalOnProperty(prefix = "eparly.local", name = "shutdown-file")
public class LocalShutdownFileWatcher {
    private static final Logger log = LoggerFactory.getLogger(LocalShutdownFileWatcher.class);

    private final ApplicationContext context;
    private final Path file;
    private final AtomicBoolean stopping = new AtomicBoolean();

    public LocalShutdownFileWatcher(ApplicationContext context,
                                    @org.springframework.beans.factory.annotation.Value("${eparly.local.shutdown-file}")
                                    String file) {
        this.context = context;
        this.file = Path.of(file);
    }

    @Scheduled(fixedDelayString = "PT3S")
    public void check() {
        if (Files.exists(file) && stopping.compareAndSet(false, true)) {
            log.info("Local shutdown requested");
            Thread thread = new Thread(() -> System.exit(SpringApplication.exit(context, () -> 0)), "local-shutdown");
            thread.start();
        }
    }
}
