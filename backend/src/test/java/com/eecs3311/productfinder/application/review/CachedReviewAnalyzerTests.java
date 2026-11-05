package com.eecs3311.productfinder.application.review;

import com.eecs3311.productfinder.domain.catalog.Listing;
import com.eecs3311.productfinder.domain.catalog.Product;
import com.eecs3311.productfinder.domain.catalog.ProductCategory;
import com.eecs3311.productfinder.domain.catalog.ProductCondition;
import com.eecs3311.productfinder.domain.catalog.Retailer;
import com.eecs3311.productfinder.domain.catalog.Review;
import com.eecs3311.productfinder.infrastructure.persistence.ProductRepository;
import com.eecs3311.productfinder.infrastructure.persistence.ReviewRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

class CachedReviewAnalyzerTests {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void cachesTrustResultsAndRecomputesWhenReviewContentChanges() {
        ReviewRepository reviews = mock(ReviewRepository.class);
        ProductRepository products = mock(ProductRepository.class);
        LLMReviewAnalyzer delegate = mock(LLMReviewAnalyzer.class);
        Review first = review("Retailer A", "Good phone.");
        Review changed = review("Retailer A", "Poor phone.");
        when(reviews.findByProductIdOrderByReviewedAtDesc(5L))
                .thenReturn(List.of(first), List.of(first), List.of(changed));
        when(delegate.analyzeTrust(anyList())).thenReturn(
                new ReviewTrustResult("Trustworthy Reviews"),
                new ReviewTrustResult("Highly Suspicious Reviews"));
        CachedReviewAnalyzer analyzer = new CachedReviewAnalyzer(reviews, products, delegate, objectMapper);

        assertThat(analyzer.analyzeTrust(5).trustPhrase()).isEqualTo("Trustworthy Reviews");
        assertThat(analyzer.analyzeTrust(5).trustPhrase()).isEqualTo("Trustworthy Reviews");
        assertThat(analyzer.analyzeTrust(5).trustPhrase()).isEqualTo("Highly Suspicious Reviews");

        verify(delegate, times(2)).analyzeTrust(anyList());
    }

    @Test
    void summaryCacheKeyIncludesProductContentAndAnalysisType() throws Exception {
        ReviewRepository reviews = mock(ReviewRepository.class);
        when(reviews.findByProductIdOrderByReviewedAtDesc(6L)).thenReturn(List.of());
        ProductRepository products = mock(ProductRepository.class);
        when(products.findProductWithListingsById(6L)).thenReturn(
                Optional.of(product(6L, "Original description")),
                Optional.of(product(6L, "Updated description")));
        LLMReviewAnalyzer delegate = mock(LLMReviewAnalyzer.class);
        when(delegate.summarize(any(Product.class), anyList())).thenReturn(
                new ReviewSummaryResult("Original summary."),
                new ReviewSummaryResult("Updated summary."));
        CachedReviewAnalyzer analyzer = new CachedReviewAnalyzer(reviews, products, delegate, objectMapper);

        assertThat(analyzer.summarize(6).summary()).isEqualTo("Original summary.");
        assertThat(analyzer.summarize(6).summary()).isEqualTo("Updated summary.");

        verify(delegate, times(2)).summarize(any(Product.class), anyList());
        assertThat(analyzer.cacheKey(6, ReviewAnalysisType.TRUST, "same"))
                .isNotEqualTo(analyzer.cacheKey(6, ReviewAnalysisType.SUMMARY, "same"));
    }

    @Test
    void summaryCacheInvalidatesWhenEligibleReviewContentChanges() throws Exception {
        Retailer retailer = retailer(21L, "Priced Retailer");
        Product product = product(8L, "Description");
        new Listing(product, retailer, new BigDecimal("10.00"), ProductCondition.NEW, true);
        ReviewRepository reviews = mock(ReviewRepository.class);
        when(reviews.findByProductIdOrderByReviewedAtDesc(8L)).thenReturn(
                List.of(review(retailer, "First review.")),
                List.of(review(retailer, "Updated review.")));
        ProductRepository products = mock(ProductRepository.class);
        when(products.findProductWithListingsById(8L)).thenReturn(Optional.of(product));
        LLMReviewAnalyzer delegate = mock(LLMReviewAnalyzer.class);
        when(delegate.summarize(any(Product.class), anyList())).thenReturn(
                new ReviewSummaryResult("First summary."),
                new ReviewSummaryResult("Updated summary."));
        CachedReviewAnalyzer analyzer = new CachedReviewAnalyzer(reviews, products, delegate, objectMapper);

        assertThat(analyzer.summarize(8).summary()).isEqualTo("First summary.");
        assertThat(analyzer.summarize(8).summary()).isEqualTo("Updated summary.");

        verify(delegate, times(2)).summarize(eq(product), anyList());
    }

    @Test
    void excludesReviewsFromRetailersWithoutPricedOffers() throws Exception {
        Retailer withPrice = retailer(11L, "Priced Retailer");
        Retailer missingPrice = retailer(12L, "Unpriced Retailer");
        Product product = product(7L, "Description");
        new Listing(product, withPrice, new BigDecimal("10.00"), ProductCondition.NEW, true);
        new Listing(product, missingPrice, null, ProductCondition.NEW, true);
        setId(product, 7L);
        ReviewRepository reviews = mock(ReviewRepository.class);
        when(reviews.findByProductIdOrderByReviewedAtDesc(7L)).thenReturn(List.of(
                review(withPrice, "Good value."),
                review(missingPrice, "Unpriced listing review.")));
        ProductRepository products = mock(ProductRepository.class);
        when(products.findProductWithListingsById(7L)).thenReturn(Optional.of(product));
        LLMReviewAnalyzer delegate = mock(LLMReviewAnalyzer.class);
        when(delegate.summarize(any(Product.class), anyList())).thenReturn(new ReviewSummaryResult("Summary."));
        CachedReviewAnalyzer analyzer = new CachedReviewAnalyzer(reviews, products, delegate, objectMapper);

        analyzer.summarize(7);

        ArgumentCaptor<List<Review>> reviewCaptor = ArgumentCaptor.forClass(List.class);
        verify(delegate).summarize(eq(product), reviewCaptor.capture());
        assertThat(reviewCaptor.getValue()).extracting(Review::getText).containsExactly("Good value.");
    }

    private static Product product(long id, String description) throws Exception {
        Product product = new Product("Phone", "Brand", "M-1", ProductCategory.SMARTPHONE, description,
                null, Map.of("storageGb", 128));
        setId(product, id);
        return product;
    }

    private static void setId(Product product, long id) throws Exception {
        Field field = Product.class.getDeclaredField("id");
        field.setAccessible(true);
        field.set(product, id);
    }

    private static Retailer retailer(long id, String name) throws Exception {
        Retailer retailer = new Retailer(name);
        Field field = Retailer.class.getDeclaredField("id");
        field.setAccessible(true);
        field.set(retailer, id);
        return retailer;
    }

    private static Review review(String retailer, String text) {
        return review(new Retailer(retailer), text);
    }

    private static Review review(Retailer retailer, String text) {
        Product product = new Product("Phone", "Brand", "M-1", ProductCategory.SMARTPHONE, "Description",
                null, Map.of());
        return new Review(product, retailer, new BigDecimal("4.5"), text,
                OffsetDateTime.parse("2026-01-01T10:00:00Z"));
    }
}
