package com.eecs3311.productfinder.domain.savedlist;

public class SavedItemNotFoundException extends RuntimeException {

    public SavedItemNotFoundException(long productId) {
        super("Product is not in the user's saved list: " + productId);
    }
}
