package com.eecs3311.productfinder.domain.user;

import java.util.List;

public class ProfileValidationException extends IllegalArgumentException {

    private final List<String> violations;

    public ProfileValidationException(List<String> violations) {
        super("Invalid profile settings: " + String.join("; ", violations));
        this.violations = List.copyOf(violations);
    }

    public List<String> getViolations() {
        return violations;
    }
}
