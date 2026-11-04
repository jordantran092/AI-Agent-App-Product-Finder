package com.eecs3311.productfinder.application;

import com.eecs3311.productfinder.domain.catalog.Listing;
import com.eecs3311.productfinder.domain.catalog.Product;
import com.eecs3311.productfinder.domain.catalog.ProductCategory;
import com.eecs3311.productfinder.domain.catalog.ProductNotFoundException;
import com.eecs3311.productfinder.infrastructure.persistence.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final ProfileService profileService;
    private final PriceComparisonService priceComparisonService;
    private final ProductSpecSchemaRegistry schemaRegistry;

    public ProductService(ProductRepository productRepository, ProfileService profileService,
                          PriceComparisonService priceComparisonService,
                          ProductSpecSchemaRegistry schemaRegistry) {
        this.productRepository = productRepository;
        this.profileService = profileService;
        this.priceComparisonService = priceComparisonService;
        this.schemaRegistry = schemaRegistry;
    }

    @Transactional(readOnly = true)
    public Product findById(long productId) {
        Product product = productRepository.findProductWithListingsById(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));
        validateSpecs(product);
        return product;
    }

    @Transactional(readOnly = true)
    public Product findByModelName(String model) {
        Objects.requireNonNull(model, "model must not be null");
        Product product = productRepository.findByModelIgnoreCase(model.trim())
                .orElseThrow(() -> new ProductNotFoundException(model));
        validateSpecs(product);
        return product;
    }

    @Transactional(readOnly = true)
    public List<Product> findByCategory(ProductCategory category) {
        return productRepository.findByCategory(category).stream()
                .map(product -> {
                    validateSpecs(product);
                    return product;
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<Listing> bestPrice(long userId, long productId) {
        Product product = findById(productId);
        ProfileSettings profile = profileService.get(userId);
        return priceComparisonService.bestListing(product, profile);
    }

    private void validateSpecs(Product product) {
        schemaRegistry.validateOrThrow(product.getCategory(), product.getAttributes());
    }
}
