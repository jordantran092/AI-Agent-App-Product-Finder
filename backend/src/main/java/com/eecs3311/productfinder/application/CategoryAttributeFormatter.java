package com.eecs3311.productfinder.application;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

final class CategoryAttributeFormatter {

    private CategoryAttributeFormatter() {
    }

    static String format(Map<String, Object> attributes) {
        return attributes.entrySet().stream()
                .map(entry -> formatLabel(entry.getKey()) + " " + formatValue(entry.getValue(), unit(entry.getKey())))
                .collect(Collectors.joining(", "));
    }

    private static String formatLabel(String key) {
        String label = key.replaceAll("([a-z0-9])([A-Z])", "$1 $2");
        for (String suffix : List.of("Gb", "Mah", "Inches", "Hz", "Hours")) {
            if (label.endsWith(" " + suffix)) {
                label = label.substring(0, label.length() - suffix.length() - 1);
                break;
            }
        }
        return capitalize(label);
    }

    private static String unit(String key) {
        if (key.endsWith("Gb")) {
            return " GB";
        }
        if (key.endsWith("Mah")) {
            return " mAh";
        }
        if (key.endsWith("Inches")) {
            return " inches";
        }
        if (key.endsWith("Hz")) {
            return " Hz";
        }
        if (key.endsWith("Hours")) {
            return " hours";
        }
        return "";
    }

    private static String formatValue(Object value, String unit) {
        if (value instanceof Boolean bool) {
            return bool ? "Yes" : "No";
        }
        if (value instanceof List<?> values) {
            return values.stream().map(item -> formatValue(item, "")).collect(Collectors.joining(", "));
        }
        if (value instanceof Map<?, ?> values) {
            return values.entrySet().stream()
                    .map(entry -> formatLabel(String.valueOf(entry.getKey())) + " "
                            + formatValue(entry.getValue(), ""))
                    .collect(Collectors.joining(", "));
        }
        return capitalize(String.valueOf(value)) + unit;
    }

    private static String capitalize(String value) {
        if (value.isEmpty()) {
            return value;
        }
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }
}
