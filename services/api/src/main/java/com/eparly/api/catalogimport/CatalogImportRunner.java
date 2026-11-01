package com.eparly.api.catalogimport;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

@Component
@Order(1000)
public class CatalogImportRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(CatalogImportRunner.class);

    private final CatalogImportProperties properties;
    private final ManualCatalogImportService service;

    public CatalogImportRunner(CatalogImportProperties properties, ManualCatalogImportService service) {
        this.properties = properties;
        this.service = service;
    }

    @Override
    public void run(String... args) {
        boolean apply = properties.isApply();
        Path file = properties.getFile();
        for (String arg : args) {
            if ("--apply".equals(arg) || "--import-apply".equals(arg)) apply = true;
            if (arg.startsWith("--file=")) file = Paths.get(arg.substring("--file=".length()));
            if (arg.startsWith("--catalog-import-file=")) file = Paths.get(arg.substring("--catalog-import-file=".length()));
        }
        if (!properties.isEnabled() && file == null) {
            return;
        }
        if (file == null) {
            log.info("Catalog import enabled but no file configured; skipping.");
            return;
        }
        log.info("Starting manual catalog import from {} (apply={})", file, apply);
        if (Files.isDirectory(file)) {
            List<CatalogImportReport> reports = service.importDirectory(file, apply);
            for (CatalogImportReport r : reports) logReport(r);
        } else {
            CatalogImportReport r = service.importFile(file, apply);
            logReport(r);
        }
        log.info("Manual catalog import finished.");
    }

    private void logReport(CatalogImportReport r) {
        log.info("Import report {} apply={} providers planned/created/existing={}/{}/{} offers planned/created/existing/skipped={}/{}/{}/{} errors={} actions={}",
                r.file(), r.apply(), r.providersPlanned(), r.providersCreated(), r.providersExisting(),
                r.offersPlanned(), r.offersCreated(), r.offersExisting(), r.offersSkipped(),
                r.errors().size(), r.actions().size());
        for (String e : r.errors()) log.warn("Import error: {}", e);
        for (String a : r.actions()) log.info("Import action: {}", a);
    }
}