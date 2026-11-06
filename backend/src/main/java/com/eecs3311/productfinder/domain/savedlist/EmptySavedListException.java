package com.eecs3311.productfinder.domain.savedlist;

public class EmptySavedListException extends RuntimeException {

    public EmptySavedListException() {
        super("Add a product before sharing");
    }
}
