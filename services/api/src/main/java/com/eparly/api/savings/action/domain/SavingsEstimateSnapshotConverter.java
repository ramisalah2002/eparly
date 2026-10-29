package com.eparly.api.savings.action.domain;

import com.eparly.api.savings.action.dto.SavingsEstimateSnapshot;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;

@Converter(autoApply = false)
public class SavingsEstimateSnapshotConverter implements AttributeConverter<SavingsEstimateSnapshot, String> {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String convertToDatabaseColumn(SavingsEstimateSnapshot attribute) {
        if (attribute == null) return null;
        try {
            return objectMapper.writeValueAsString(attribute);
        } catch (IOException e) {
            throw new IllegalStateException("Impossible de sérialiser l'estimation d'économies", e);
        }
    }

    @Override
    public SavingsEstimateSnapshot convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) return null;
        try {
            return objectMapper.readValue(dbData, SavingsEstimateSnapshot.class);
        } catch (IOException e) {
            throw new IllegalStateException("Impossible de désérialiser l'estimation d'économies", e);
        }
    }
}
