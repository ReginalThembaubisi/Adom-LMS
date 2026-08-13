package com.example.learnerassignments.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

@Converter(autoApply = false)
public class SastToUtcConverter implements AttributeConverter<LocalDateTime, LocalDateTime> {

    private static final ZoneId SAST = ZoneId.of("Africa/Johannesburg");

    @Override
    public LocalDateTime convertToDatabaseColumn(LocalDateTime attribute) {
        if (attribute == null) {
            return null;
        }
        return attribute.atZone(SAST)
                .withZoneSameInstant(ZoneOffset.UTC)
                .toLocalDateTime();
    }

    @Override
    public LocalDateTime convertToEntityAttribute(LocalDateTime dbData) {
        if (dbData == null) {
            return null;
        }
        return dbData.atZone(ZoneOffset.UTC)
                .withZoneSameInstant(SAST)
                .toLocalDateTime();
    }
}
