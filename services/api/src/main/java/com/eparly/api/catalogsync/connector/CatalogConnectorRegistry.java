package com.eparly.api.catalogsync.connector;

import com.eparly.api.catalogsync.domain.CatalogSyncSource;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

@Component
public class CatalogConnectorRegistry {
    private final List<CatalogSourceConnector> connectors;
    private final GenericOfficialSourceConnector genericConnector;

    public CatalogConnectorRegistry(List<CatalogSourceConnector> connectors,
                                    GenericOfficialSourceConnector genericConnector) {
        this.connectors = connectors.stream().sorted(Comparator.comparing(CatalogSourceConnector::key)).toList();
        this.genericConnector = genericConnector;
    }

    public CatalogSourceConnector connectorFor(CatalogSyncSource source) {
        if (source.connectorKey() == null || source.connectorKey().isBlank()) return genericConnector;
        return connectors.stream().filter(connector -> connector.key().equals(source.connectorKey())).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown catalog connector"));
    }

    public boolean isKnown(String key) {
        return connectors.stream().anyMatch(connector -> connector.key().equals(key));
    }

    public List<CatalogSourceConnector> all() {
        return connectors;
    }
}
