package com.eecs3311.productfinder.domain.savedlist;

import com.eecs3311.productfinder.domain.catalog.Product;
import com.eecs3311.productfinder.domain.catalog.ProductCategory;
import com.eecs3311.productfinder.domain.user.User;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

class SavedListTests {

    @Test
    void cannotShareAnEmptyListAndRemovingLastItemRevokesShareToken() {
        SavedList savedList = new SavedList(new User("test-user", "hashed-password"));
        assertThatIllegalStateException()
                .isThrownBy(() -> savedList.setShareToken("share-token"));

        Product product = product("Model One");
        savedList.addItem(product);
        savedList.setShareToken("share-token");
        savedList.removeItem(product);

        assertThat(savedList.getShareToken()).isNull();
        assertThat(savedList.isEmpty()).isTrue();
    }

    @Test
    void rejectsDuplicateProductsByModelBeforeTheyHaveDatabaseIds() {
        SavedList savedList = new SavedList(new User("test-user", "hashed-password"));
        savedList.addItem(product("MODEL ONE"));

        assertThatIllegalStateException()
                .isThrownBy(() -> savedList.addItem(product("model one")))
                .withMessageContaining("already");
    }

    private Product product(String model) {
        return new Product("Test Product", "Test Brand", model, ProductCategory.SMARTPHONE, "Synthetic product",
                null, Map.of());
    }
}
