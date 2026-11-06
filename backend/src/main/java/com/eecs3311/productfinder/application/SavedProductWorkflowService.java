package com.eecs3311.productfinder.application;

import com.eecs3311.productfinder.application.review.ReviewAnalyzer;
import com.eecs3311.productfinder.application.review.ReviewSummaryResult;
import com.eecs3311.productfinder.application.review.ReviewTrustResult;
import com.eecs3311.productfinder.domain.catalog.Product;
import com.eecs3311.productfinder.domain.catalog.ProductNotFoundException;
import com.eecs3311.productfinder.domain.savedlist.SavedItem;
import com.eecs3311.productfinder.domain.savedlist.SavedItemNotFoundException;
import com.eecs3311.productfinder.domain.savedlist.SavedList;
import com.eecs3311.productfinder.infrastructure.persistence.ProductRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
public class SavedProductWorkflowService {

    private final ProductRepository productRepository;
    private final SavedListService savedListService;
    private final AgentService agentService;
    private final ProductService productService;
    private final ReviewAnalyzer reviewAnalyzer;

    public SavedProductWorkflowService(ProductRepository productRepository, SavedListService savedListService,
                                       AgentService agentService,
                                       ProductService productService,
                                       ReviewAnalyzer reviewAnalyzer) {
        this.productRepository = productRepository;
        this.savedListService = savedListService;
        this.agentService = agentService;
        this.productService = productService;
        this.reviewAnalyzer = reviewAnalyzer;
    }

    public List<SavedItem> getSavedItems(long userId) {
        return savedListService.get(userId).map(SavedList::getItems).orElseGet(List::of);
    }

    public ProductAddResult addOrSuggest(long userId, String productName) {
        if (productName == null || productName.isBlank() || productName.length() > 200) {
            throw new IllegalArgumentException("Product name must contain 1 to 200 characters");
        }
        String normalizedName = productName.trim();
        List<Product> products = productRepository.findByNameIgnoreCaseOrderByIdAsc(normalizedName);
        if (!products.isEmpty()) {
            return addKnownProduct(userId, products.getFirst());
        }
        return agentService.suggestSimilar(normalizedName)
                .map(candidate -> new ProductAddResult(ProductAddStatus.SUGGESTION, candidate, null))
                .orElseGet(() -> new ProductAddResult(ProductAddStatus.NOT_FOUND, null, null));
    }

    public ProductAddResult confirmAdd(long userId, long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));
        return addKnownProduct(userId, product);
    }

    public ProductPageResult productPage(long userId, long productId) {
        SavedItem savedItem = savedListService.findSavedItem(userId, productId)
                .orElseThrow(() -> new SavedItemNotFoundException(productId));
        Product product = productService.findById(productId);
        if (product.isDiscontinued()) {
            savedListService.removeProduct(userId, productId);
            return ProductPageResult.discontinuedResult();
        }
        savedItem = refreshTrustLabel(userId, productId, savedItem);
        return new ProductPageResult(false, product, savedItem, productService.bestPrice(userId, productId)
                .orElse(null));
    }

    public ReviewSummaryResult summarize(long userId, long productId) {
        savedListService.findSavedItem(userId, productId)
                .orElseThrow(() -> new SavedItemNotFoundException(productId));
        return productService.reviewInsights(productId);
    }

    public NoteSaveResult saveNote(long userId, long productId, String note) {
        if (note != null && note.length() > 4000) {
            throw new IllegalArgumentException("Note must not exceed 4000 characters");
        }
        StringBuilder sanitized = new StringBuilder(note == null ? 0 : note.length());
        boolean changed = false;
        if (note != null) {
            for (int index = 0; index < note.length(); index++) {
                char character = note.charAt(index);
                if (Character.isISOControl(character) && character != '\n'
                        && character != '\r' && character != '\t') {
                    changed = true;
                } else {
                    sanitized.append(character);
                }
            }
        }
        String savedNote = sanitized.length() == 0 ? null : sanitized.toString();
        savedListService.saveNote(userId, productId, savedNote);
        return new NoteSaveResult(savedNote, changed);
    }

    public void remove(long userId, long productId) {
        savedListService.removeProduct(userId, productId);
    }

    private ProductAddResult addKnownProduct(long userId, Product product) {
        Optional<SavedItem> existingItem = savedListService.findSavedItem(userId, product.getId());
        if (existingItem.isPresent()) {
            SavedItem savedItem = refreshTrustLabel(userId, product.getId(), existingItem.get());
            return new ProductAddResult(ProductAddStatus.ALREADY_SAVED, product, savedItem.getTrustLabel());
        }
        savedListService.addProductById(userId, product.getId());
        SavedItem savedItem = savedListService.findSavedItem(userId, product.getId())
                .orElseThrow(() -> new IllegalStateException("Added product is missing from its saved list"));
        savedItem = refreshTrustLabel(userId, product.getId(), savedItem);
        return new ProductAddResult(ProductAddStatus.ADDED, product, savedItem.getTrustLabel());
    }

    private SavedItem refreshTrustLabel(long userId, long productId, SavedItem item) {
        ReviewTrustResult result = reviewAnalyzer.analyzeTrust(productId);
        if (Objects.equals(item.getTrustLabel(), result.trustPhrase())) {
            return item;
        }
        return savedListService.updateTrustLabel(userId, productId, result.trustPhrase());
    }
}
