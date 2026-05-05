package com.simfat.backend.model;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

@Converter
public class UserRoleSetConverter implements AttributeConverter<Set<UserRole>, String> {

    @Override
    public String convertToDatabaseColumn(Set<UserRole> attribute) {
        if (attribute == null || attribute.isEmpty()) {
            return "";
        }
        return attribute.stream().map(Enum::name).sorted().collect(Collectors.joining(","));
    }

    @Override
    public Set<UserRole> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return Collections.emptySet();
        }
        return Arrays.stream(dbData.split(","))
            .map(String::trim)
            .filter(value -> !value.isBlank())
            .map(UserRole::valueOf)
            .collect(Collectors.toSet());
    }
}

