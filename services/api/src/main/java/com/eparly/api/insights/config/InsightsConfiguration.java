package com.eparly.api.insights.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(InsightsProperties.class)
public class InsightsConfiguration {
}
