package com.eecs3311.productfinder.api.savedlist;

import com.eecs3311.productfinder.domain.savedlist.SavedItem;

import java.util.List;

public record SavedListResponse(List<SavedItemResponse> items) {

    public SavedListResponse {
        items = List.copyOf(items);
    }

    public static SavedListResponse from(List<SavedItem> items) {
        return new SavedListResponse(items.stream().map(SavedItemResponse::from).toList());
    }
}
