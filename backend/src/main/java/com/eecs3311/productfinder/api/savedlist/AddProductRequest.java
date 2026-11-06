package com.eecs3311.productfinder.api.savedlist;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AddProductRequest(@NotBlank @Size(max = 200) String productName) {
}
