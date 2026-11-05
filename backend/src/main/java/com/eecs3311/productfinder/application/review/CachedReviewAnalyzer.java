package com.eecs3311.productfinder.application.review;

import com.eecs3311.productfinder.domain.catalog.Product;
import com.eecs3311.productfinder.domain.catalog.ProductNotFoundException;
import com.eecs3311.productfinder.domain.catalog.Review;
import com.eecs3311.productfinder.infrastructure.persistence.ProductRepository;
import com.eecs3311.productfinder.infrastructure.persistence.ReviewRepository;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
public class CachedReviewAnalyzer implements ReviewAnalyzer {

    private final ReviewRepository reviewRepository;
    private final ProductRepository productRepository;
    private final LLMReviewAnalyzer delegate;
    private final ObjectMapper objectMapper;
    private final ConcurrentMap<ReviewAnalysisCacheKey, ReviewAnalysisResult> cache = new ConcurrentHashMap<>();

    public CachedReviewAnalyzer(ReviewRepository reviewRepository, ProductRepository productRepository,
                                LLMReviewAnalyzer delegate, ObjectMapper objectMapper) {
        this.reviewRepository = reviewRepository;
        this.productRepository = productRepository;
        this.delegate = delegate;
        this.objectMapper = objectMapper;
    }

    @Override
    public ReviewTrustResult analyzeTrust(long productId) {
        List<Review> reviews = sortedReviews(reviewRepository.findByProductIdOrderByReviewedAtDesc(productId));
        ReviewAnalysisCacheKey key = cacheKey(productId, ReviewAnalysisType.TRUST, reviewFingerprint(reviews));
        return (ReviewTrustResult) cache.computeIfAbsent(key, ignored -> delegate.analyzeTrust(reviews));
    }

    @Override
    public ReviewSummaryResult summarize(long productId) {
        Product product = productRepository.findProductWithListingsById(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));
        List<Review> reviews = sortedReviews(reviewRepository.findByProductIdOrderByReviewedAtDesc(productId));
        List<Review> eligibleReviews = reviews.stream()
                .filter(review -> product.getListings().stream()
                        .anyMatch(listing -> listing.getPrice() != null
                                && Objects.equals(listing.getRetailer().getId(), review.getRetailer().getId())))
                .toList();
        String fingerprint = summaryFingerprint(product, eligibleReviews);
        ReviewAnalysisCacheKey key = cacheKey(productId, ReviewAnalysisType.SUMMARY, fingerprint);
        return (ReviewSummaryResult) cache.computeIfAbsent(key,
                ignored -> delegate.summarize(product, eligibleReviews));
    }

    ReviewAnalysisCacheKey cacheKey(long productId, ReviewAnalysisType type, String fingerprint) {
        return new ReviewAnalysisCacheKey(productId, type, fingerprint);
    }

    private List<Review> sortedReviews(List<Review> reviews) {
        return reviews.stream()
                .sorted(Comparator.comparing((Review review) -> review.getRetailer().getName())
                        .thenComparing(Review::getReviewedAt)
                        .thenComparing(review -> review.getRating().toPlainString())
                        .thenComparing(Review::getText))
                .toList();
    }

    private String reviewFingerprint(List<Review> reviews) {
        List<Map<String, Object>> content = reviews.stream().map(review -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("retailer", review.getRetailer().getName());
            row.put("rating", review.getRating());
            row.put("text", review.getText());
            row.put("reviewedAt", review.getReviewedAt().toString());
            return row;
        }).toList();
        return fingerprint(content);
    }

    private String summaryFingerprint(Product product, List<Review> reviews) {
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("name", product.getName());
        content.put("brand", product.getBrand());
        content.put("model", product.getModel());
        content.put("category", product.getCategory());
        content.put("description", product.getDescription());
        content.put("attributes", canonicalValue(product.getAttributes()));
        List<Map<String, Object>> offers = new ArrayList<>();
        product.getListings().stream()
                .filter(listing -> listing.getPrice() != null)
                .sorted(Comparator.comparing(listing -> listing.getRetailer().getName()))
                .forEach(listing -> {
                    Map<String, Object> offer = new LinkedHashMap<>();
                    offer.put("retailer", listing.getRetailer().getName());
                    offer.put("price", listing.getPrice());
                    offer.put("condition", listing.getCondition());
                    offer.put("inStock", listing.isInStock());
                    offers.add(offer);
                });
        content.put("pricedRetailerOffers", offers);
        content.put("reviews", reviewFingerprintContent(reviews));
        return fingerprint(content);
    }

    private List<Map<String, Object>> reviewFingerprintContent(List<Review> reviews) {
        return reviews.stream().map(review -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("retailer", review.getRetailer().getName());
            row.put("rating", review.getRating());
            row.put("text", review.getText());
            row.put("reviewedAt", review.getReviewedAt().toString());
            return row;
        }).toList();
    }

    private String fingerprint(Object content) {
        try {
            byte[] serialized = objectMapper.writeValueAsBytes(canonicalValue(content));
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(serialized);
            return HexFormat.of().formatHex(digest);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Unable to serialize review content fingerprint", exception);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private Object canonicalValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> sorted = new TreeMap<>();
            map.forEach((key, mapValue) -> sorted.put(String.valueOf(key), canonicalValue(mapValue)));
            return sorted;
        }
        if (value instanceof List<?> list) {
            return list.stream().map(this::canonicalValue).toList();
        }
        return value;
    }
}
