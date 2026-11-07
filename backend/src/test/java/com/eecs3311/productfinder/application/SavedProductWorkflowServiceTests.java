package com.eecs3311.productfinder.application;

import com.eecs3311.productfinder.domain.catalog.Product;
import com.eecs3311.productfinder.domain.catalog.ProductCategory;
import com.eecs3311.productfinder.domain.catalog.Listing;
import com.eecs3311.productfinder.domain.savedlist.SavedItem;
import com.eecs3311.productfinder.domain.savedlist.SavedItemNotFoundException;
import com.eecs3311.productfinder.application.review.ReviewAnalyzer;
import com.eecs3311.productfinder.application.review.ReviewTrustResult;
import com.eecs3311.productfinder.infrastructure.persistence.ProductRepository;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class SavedProductWorkflowServiceTests {

    @Test
    void removesUnsupportedControlCharactersAndReportsSanitization() {
        SavedListService savedListService = mock(SavedListService.class);
        SavedProductWorkflowService workflow = workflow(savedListService);
        String note = "keep" + (char) 1 + " this";

        NoteSaveResult result = workflow.saveNote(1, 2, note);

        assertThat(result.note()).isEqualTo("keep this");
        assertThat(result.sanitized()).isTrue();
        verify(savedListService).saveNote(1, 2, "keep this");
    }

    @Test
    void rejectsOversizedNotesBeforeTryingToPersist() {
        SavedListService savedListService = mock(SavedListService.class);
        SavedProductWorkflowService workflow = workflow(savedListService);

        assertThatThrownBy(() -> workflow.saveNote(1, 2, "x".repeat(4001)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("4000");
    }

    @Test
    void suggestionsDoNotAddUntilTheUserConfirms() throws Exception {
        Product candidate = product(22L);
        ProductRepository products = mock(ProductRepository.class);
        when(products.findByNameIgnoreCaseOrderByIdAsc("Orion 15 128GB")).thenReturn(java.util.List.of());
        when(products.findById(22L)).thenReturn(Optional.of(candidate));
        SavedListService savedListService = mock(SavedListService.class);
        SavedItem savedItem = mock(SavedItem.class);
        when(savedItem.getTrustLabel()).thenReturn(null, "Trustworthy Reviews");
        when(savedListService.findSavedItem(5L, 22L))
                .thenReturn(Optional.empty(), Optional.of(savedItem));
        when(savedListService.updateTrustLabel(5L, 22L, "Trustworthy Reviews")).thenReturn(savedItem);
        AgentService agentService = mock(AgentService.class);
        when(agentService.suggestSimilar("Orion 15 128GB")).thenReturn(Optional.of(candidate));
        ReviewAnalyzer reviewAnalyzer = mock(ReviewAnalyzer.class);
        when(reviewAnalyzer.analyzeTrust(22L)).thenReturn(new ReviewTrustResult("Trustworthy Reviews"));
        SavedProductWorkflowService workflow = new SavedProductWorkflowService(
                products, savedListService, agentService, mock(ProductService.class),
                reviewAnalyzer);

        ProductAddResult suggestion = workflow.addOrSuggest(5, "Orion 15 128GB");

        assertThat(suggestion.status()).isEqualTo(ProductAddStatus.SUGGESTION);
        verify(savedListService, never()).addProductById(anyLong(), anyLong());
        ProductAddResult accepted = workflow.confirmAdd(5, 22);
        assertThat(accepted.status()).isEqualTo(ProductAddStatus.ADDED);
        assertThat(accepted.trustLabel()).isEqualTo("Trustworthy Reviews");
        verify(savedListService).addProductById(5, 22);
        verify(savedListService).updateTrustLabel(5, 22, "Trustworthy Reviews");
    }

    @Test
    void addsAnExactProductNameWithoutAskingForItsCatalogModelNumber() throws Exception {
        Product product = product(23L);
        ProductRepository products = mock(ProductRepository.class);
        when(products.findByNameIgnoreCaseOrderByIdAsc("Orion 15")).thenReturn(java.util.List.of(product));
        SavedListService savedListService = mock(SavedListService.class);
        SavedItem savedItem = mock(SavedItem.class);
        when(savedItem.getTrustLabel()).thenReturn("Trustworthy Reviews");
        when(savedListService.findSavedItem(5L, 23L)).thenReturn(Optional.empty(), Optional.of(savedItem));
        when(savedItem.getTrustLabel()).thenReturn("Trustworthy Reviews");
        ReviewAnalyzer reviewAnalyzer = mock(ReviewAnalyzer.class);
        when(reviewAnalyzer.analyzeTrust(23L)).thenReturn(new ReviewTrustResult("Trustworthy Reviews"));
        ProductService productService = mock(ProductService.class);
        SavedProductWorkflowService workflow = new SavedProductWorkflowService(
                products, savedListService, mock(AgentService.class), productService, reviewAnalyzer);

        ProductAddResult result = workflow.addOrSuggest(5, " Orion 15 ");

        assertThat(result.status()).isEqualTo(ProductAddStatus.ADDED);
        verify(products).findByNameIgnoreCaseOrderByIdAsc("Orion 15");
        verify(products, never()).findByModelIgnoreCase(anyString());
        verify(savedListService).addProductById(5, 23);
        verify(productService, never()).bestPrice(anyLong(), anyLong());
    }

    @Test
    void productDetailRemovesDiscontinuedSavedProducts() throws Exception {
        Product discontinued = product(32L);
        Field field = Product.class.getDeclaredField("discontinued");
        field.setAccessible(true);
        field.setBoolean(discontinued, true);

        SavedListService savedListService = mock(SavedListService.class);
        when(savedListService.findSavedItem(7L, 32L)).thenReturn(Optional.of(mock(SavedItem.class)));
        ProductService productService = mock(ProductService.class);
        ReviewAnalyzer reviewAnalyzer = mock(ReviewAnalyzer.class);
        when(reviewAnalyzer.analyzeTrust(32L)).thenReturn(new ReviewTrustResult("Trustworthy Reviews"));
        when(productService.findById(32L)).thenReturn(discontinued);
        SavedProductWorkflowService workflow = new SavedProductWorkflowService(
                mock(ProductRepository.class),
                savedListService,
                mock(AgentService.class),
                productService,
                reviewAnalyzer
        );

        assertThat(workflow.productPage(7, 32).discontinued()).isTrue();

        verify(savedListService).removeProduct(7, 32);
        verify(productService, never()).bestPrice(7, 32);
    }

    @Test
    void productPageIsNotAvailableToUsersWhoHaveNotSavedTheProduct() {
        SavedListService savedListService = mock(SavedListService.class);
        when(savedListService.findSavedItem(9L, 44L)).thenReturn(Optional.empty());
        ProductService productService = mock(ProductService.class);
        SavedProductWorkflowService workflow = new SavedProductWorkflowService(
                mock(ProductRepository.class),
                savedListService,
                mock(AgentService.class),
                productService,
                mock(ReviewAnalyzer.class)
        );

        assertThatThrownBy(() -> workflow.productPage(9, 44))
                .isInstanceOf(SavedItemNotFoundException.class);
        assertThatThrownBy(() -> workflow.bestPrice(9, 44))
                .isInstanceOf(SavedItemNotFoundException.class);
        verifyNoInteractions(productService);
    }

    @Test
    void openingProductPageDoesNotComparePricesButExplicitRequestsDo() throws Exception {
        Product product = product(22L);
        SavedItem savedItem = mock(SavedItem.class);
        when(savedItem.getTrustLabel()).thenReturn("Trustworthy Reviews");
        SavedListService savedListService = mock(SavedListService.class);
        when(savedListService.findSavedItem(5L, 22L)).thenReturn(Optional.of(savedItem));
        ProductService productService = mock(ProductService.class);
        when(productService.findById(22L)).thenReturn(product);
        Listing listing = mock(Listing.class);
        when(productService.bestPrice(5L, 22L)).thenReturn(Optional.of(listing), Optional.empty());
        ReviewAnalyzer reviewAnalyzer = mock(ReviewAnalyzer.class);
        when(reviewAnalyzer.analyzeTrust(22L)).thenReturn(new ReviewTrustResult("Trustworthy Reviews"));
        SavedProductWorkflowService workflow = new SavedProductWorkflowService(
                mock(ProductRepository.class), savedListService, mock(AgentService.class), productService,
                reviewAnalyzer);

        assertThat(workflow.productPage(5, 22).product()).isSameAs(product);
        verify(productService, never()).bestPrice(anyLong(), anyLong());
        assertThat(workflow.bestPrice(5, 22).bestListing()).isSameAs(listing);
        assertThat(workflow.bestPrice(5, 22).bestListing()).isNull();
        verify(productService, times(2)).bestPrice(5, 22);
    }

    @Test
    void priceRequestRemovesDiscontinuedProductWithoutComparingPrices() throws Exception {
        Product discontinued = product(32L);
        Field field = Product.class.getDeclaredField("discontinued");
        field.setAccessible(true);
        field.setBoolean(discontinued, true);
        SavedListService savedListService = mock(SavedListService.class);
        when(savedListService.findSavedItem(7L, 32L)).thenReturn(Optional.of(mock(SavedItem.class)));
        ProductService productService = mock(ProductService.class);
        when(productService.findById(32L)).thenReturn(discontinued);
        SavedProductWorkflowService workflow = new SavedProductWorkflowService(
                mock(ProductRepository.class), savedListService, mock(AgentService.class), productService,
                mock(ReviewAnalyzer.class));

        assertThat(workflow.bestPrice(7, 32).discontinued()).isTrue();
        verify(savedListService).removeProduct(7, 32);
        verify(productService, never()).bestPrice(anyLong(), anyLong());
    }

    private static SavedProductWorkflowService workflow(SavedListService savedListService) {
        return new SavedProductWorkflowService(
                mock(ProductRepository.class),
                savedListService,
                mock(AgentService.class),
                mock(ProductService.class),
                mock(ReviewAnalyzer.class)
        );
    }

    private static Product product(long id) throws Exception {
        Product product = new Product(
                "Orion 15", "Orion", "OR-15-128", ProductCategory.SMARTPHONE, "test product", null, Map.of());
        Field field = Product.class.getDeclaredField("id");
        field.setAccessible(true);
        field.set(product, id);
        return product;
    }
}
