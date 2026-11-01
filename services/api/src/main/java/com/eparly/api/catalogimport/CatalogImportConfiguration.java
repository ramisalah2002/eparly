package com.eparly.api.catalogimport;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(CatalogImportProperties.class)
public class CatalogImportConfiguration {
}