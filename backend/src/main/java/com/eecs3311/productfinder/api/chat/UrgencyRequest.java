package com.eecs3311.productfinder.api.chat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UrgencyRequest(
        @NotBlank @Size(max = 200) String urgency
) {
}
