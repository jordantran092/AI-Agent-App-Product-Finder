package com.eecs3311.productfinder.application;

import java.util.List;

public record SharedListView(List<SharedProductView> items) {

    public SharedListView {
        items = List.copyOf(items);
    }
}
