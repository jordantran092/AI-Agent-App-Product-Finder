package com.eecs3311.productfinder.domain.chat;

import com.eecs3311.productfinder.domain.catalog.ProductCategory;
import com.eecs3311.productfinder.domain.catalog.ProductCondition;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record Requirements(
        ProductCategory category,
        BigDecimal maxPrice,
        List<String> mustHaves,
        List<String> niceToHaves,
        List<String> excludedBrands,
        ProductCondition condition,
        Map<String, Object> attributes
) {

    public Requirements {
        Objects.requireNonNull(category, "category must not be null");
        mustHaves = List.copyOf(mustHaves);
        niceToHaves = List.copyOf(niceToHaves);
        excludedBrands = List.copyOf(excludedBrands);
        attributes = immutableMap(attributes);
        if (maxPrice != null && maxPrice.signum() < 0) {
            throw new IllegalArgumentException("maxPrice must not be negative");
        }
    }

    public Requirements(
            ProductCategory category,
            BigDecimal maxPrice,
            List<String> mustHaves,
            List<String> niceToHaves,
            List<String> excludedBrands,
            ProductCondition condition
    ) {
        this(category, maxPrice, mustHaves, niceToHaves, excludedBrands, condition, Map.of());
    }

    private static Map<String, Object> immutableMap(Map<String, Object> source) {
        Objects.requireNonNull(source, "attributes must not be null");
        Map<String, Object> copy = new LinkedHashMap<>();
        source.forEach((key, value) -> copy.put(
                Objects.requireNonNull(key, "attribute names must not be null"),
                immutableValue(value)
        ));
        return Collections.unmodifiableMap(copy);
    }

    private static Object immutableValue(Object value) {
        if (value instanceof Map<?, ?> source) {
            Map<String, Object> copy = new LinkedHashMap<>();
            source.forEach((key, nestedValue) -> {
                if (!(key instanceof String name)) {
                    throw new IllegalArgumentException("attribute object keys must be strings");
                }
                copy.put(name, immutableValue(nestedValue));
            });
            return Collections.unmodifiableMap(copy);
        }
        if (value instanceof List<?> source) {
            List<Object> copy = new ArrayList<>(source.size());
            source.forEach(element -> copy.add(immutableValue(element)));
            return List.copyOf(copy);
        }
        return value;
    }
}
