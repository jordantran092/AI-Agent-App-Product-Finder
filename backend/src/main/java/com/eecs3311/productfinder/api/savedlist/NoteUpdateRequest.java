package com.eecs3311.productfinder.api.savedlist;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record NoteUpdateRequest(@NotNull @Size(max = 4000) String note) {
}
