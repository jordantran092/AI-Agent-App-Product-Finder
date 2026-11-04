package com.eecs3311.productfinder.api.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank
        @Size(min = 3, max = 80)
        String username,
        @NotBlank
        @Size(max = 72)
        String password
) {
}
