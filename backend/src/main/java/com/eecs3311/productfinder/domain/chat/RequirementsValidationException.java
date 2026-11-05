package com.eecs3311.productfinder.domain.chat;

import java.util.List;

public class RequirementsValidationException extends IllegalArgumentException {

    private final List<String> violations;

    public RequirementsValidationException(List<String> violations) {
        super("Invalid structured requirements: " + String.join("; ", violations));
        this.violations = List.copyOf(violations);
    }

    public List<String> getViolations() {
        return violations;
    }
}
