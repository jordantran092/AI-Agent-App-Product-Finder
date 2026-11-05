package com.eecs3311.productfinder.application;

import com.eecs3311.productfinder.domain.chat.Requirements;

import java.util.Objects;

public record RequirementsExtractionResult(Requirements requirements, String followUpQuestion) {

    public RequirementsExtractionResult {
        if ((requirements == null) == (followUpQuestion == null)) {
            throw new IllegalArgumentException("Exactly one of requirements or followUpQuestion is required");
        }
        if (followUpQuestion != null && followUpQuestion.isBlank()) {
            throw new IllegalArgumentException("followUpQuestion must not be blank");
        }
    }

    public static RequirementsExtractionResult ready(Requirements requirements) {
        return new RequirementsExtractionResult(Objects.requireNonNull(requirements), null);
    }

    public static RequirementsExtractionResult followUp(String question) {
        return new RequirementsExtractionResult(null, question);
    }

    public boolean isReady() {
        return requirements != null;
    }
}
