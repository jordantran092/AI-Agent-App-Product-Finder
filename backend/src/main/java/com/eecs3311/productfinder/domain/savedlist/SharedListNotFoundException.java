package com.eecs3311.productfinder.domain.savedlist;

public class SharedListNotFoundException extends RuntimeException {

    public SharedListNotFoundException() {
        super("This list is no longer available");
    }
}
