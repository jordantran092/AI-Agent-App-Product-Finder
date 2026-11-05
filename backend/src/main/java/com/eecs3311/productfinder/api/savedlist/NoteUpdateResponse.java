package com.eecs3311.productfinder.api.savedlist;

import com.eecs3311.productfinder.application.NoteSaveResult;

public record NoteUpdateResponse(String note, boolean sanitized, String message) {

    public static NoteUpdateResponse from(NoteSaveResult result) {
        String message = result.sanitized()
                ? "Unsupported control characters were removed from the note"
                : "Note saved";
        return new NoteUpdateResponse(result.note(), result.sanitized(), message);
    }
}
