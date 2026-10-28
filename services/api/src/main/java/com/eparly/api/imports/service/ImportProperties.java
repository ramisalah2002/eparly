package com.eparly.api.imports.service;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Limites configurables du moteur d'import CSV.
 */
@ConfigurationProperties(prefix = "eparly.imports")
public record ImportProperties(
        long maxFileBytes,
        int maxRows,
        int maxColumns,
        int maxFieldLength,
        Duration processingTimeout,
        int previewSampleSize,
        int maxReportedIssues
) {
    public ImportProperties(
            @DefaultValue("5242880") long maxFileBytes,
            @DefaultValue("10000") int maxRows,
            @DefaultValue("50") int maxColumns,
            @DefaultValue("2000") int maxFieldLength,
            @DefaultValue("PT30S") Duration processingTimeout,
            @DefaultValue("20") int previewSampleSize,
            @DefaultValue("100") int maxReportedIssues
    ) {
        this.maxFileBytes = maxFileBytes;
        this.maxRows = maxRows;
        this.maxColumns = maxColumns;
        this.maxFieldLength = maxFieldLength;
        this.processingTimeout = processingTimeout;
        this.previewSampleSize = previewSampleSize;
        this.maxReportedIssues = maxReportedIssues;
    }
}
