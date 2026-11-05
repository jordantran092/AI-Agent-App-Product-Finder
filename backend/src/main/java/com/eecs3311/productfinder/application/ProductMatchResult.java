package com.eecs3311.productfinder.application;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record ProductMatchResult(ProductMatch match, String noMatchMessage, List<String> constraintsToRelax) {

    public ProductMatchResult {
        constraintsToRelax = List.copyOf(constraintsToRelax);
        if ((match == null) == (noMatchMessage == null)) {
            throw new IllegalArgumentException("Exactly one of match or noMatchMessage is required");
        }
        if (noMatchMessage != null && noMatchMessage.isBlank()) {
            throw new IllegalArgumentException("noMatchMessage must not be blank");
        }
        if (match != null && !constraintsToRelax.isEmpty()) {
            throw new IllegalArgumentException("A successful match cannot have constraints to relax");
        }
    }

    public static ProductMatchResult matched(ProductMatch match) {
        return new ProductMatchResult(Objects.requireNonNull(match), null, List.of());
    }

    public static ProductMatchResult noMatch(String message, List<String> constraintsToRelax) {
        return new ProductMatchResult(null, message, constraintsToRelax);
    }

    public Optional<ProductMatch> optionalMatch() {
        return Optional.ofNullable(match);
    }

    public boolean isMatched() {
        return match != null;
    }
}
