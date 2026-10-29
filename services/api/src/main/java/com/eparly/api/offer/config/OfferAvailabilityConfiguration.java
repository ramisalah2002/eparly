package com.eparly.api.offer.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

@Configuration
public class OfferAvailabilityConfiguration {

    @Bean
    Clock offerAvailabilityClock() {
        return Clock.system(ZoneId.of("Europe/Paris"));
    }
}
