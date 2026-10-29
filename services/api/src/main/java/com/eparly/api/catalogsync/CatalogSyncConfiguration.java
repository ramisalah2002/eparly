package com.eparly.api.catalogsync;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(CatalogSyncProperties.class)
public class CatalogSyncConfiguration {
}
