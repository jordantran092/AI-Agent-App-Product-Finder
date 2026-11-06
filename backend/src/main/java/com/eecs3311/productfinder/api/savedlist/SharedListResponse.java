package com.eecs3311.productfinder.api.savedlist;

import com.eecs3311.productfinder.application.SharedListView;

import java.util.List;

public record SharedListResponse(List<SharedProductResponse> items) {

    public SharedListResponse {
        items = List.copyOf(items);
    }

    public static SharedListResponse from(SharedListView view) {
        return new SharedListResponse(view.items().stream().map(SharedProductResponse::from).toList());
    }
}
