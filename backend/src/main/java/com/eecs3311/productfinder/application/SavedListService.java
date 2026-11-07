package com.eecs3311.productfinder.application;

import com.eecs3311.productfinder.domain.catalog.Product;
import com.eecs3311.productfinder.domain.catalog.OfferSnapshot;
import com.eecs3311.productfinder.domain.catalog.ProductNotFoundException;
import com.eecs3311.productfinder.domain.savedlist.SavedItem;
import com.eecs3311.productfinder.domain.savedlist.SavedList;
import com.eecs3311.productfinder.domain.savedlist.SavedItemNotFoundException;
import com.eecs3311.productfinder.domain.savedlist.EmptySavedListException;
import com.eecs3311.productfinder.domain.savedlist.SharedListNotFoundException;
import com.eecs3311.productfinder.domain.user.User;
import com.eecs3311.productfinder.infrastructure.persistence.ProductRepository;
import com.eecs3311.productfinder.infrastructure.persistence.SavedListRepository;
import com.eecs3311.productfinder.infrastructure.persistence.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Optional;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;

@Service
public class SavedListService {

    private static final int SHARE_TOKEN_BYTES = 32;
    private static final int MAX_TOKEN_ATTEMPTS = 5;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final SavedListRepository savedListRepository;
    private final PriceComparisonService priceComparisonService;

    public SavedListService(UserRepository userRepository, ProductRepository productRepository,
                            SavedListRepository savedListRepository,
                            PriceComparisonService priceComparisonService) {
        this.userRepository = userRepository;
        this.productRepository = productRepository;
        this.savedListRepository = savedListRepository;
        this.priceComparisonService = priceComparisonService;
    }

    @Transactional(readOnly = true)
    public Optional<SavedList> get(long userId) {
        return savedListRepository.findByUserId(userId);
    }

    @Transactional
    public String createShareLink(long userId) {
        SavedList savedList = savedListRepository.findByUserId(userId)
                .orElseThrow(EmptySavedListException::new);
        if (savedList.isEmpty()) {
            throw new EmptySavedListException();
        }
        if (savedList.getShareToken() != null) {
            return savedList.getShareToken();
        }
        String token = newShareToken();
        savedList.setShareToken(token);
        savedListRepository.saveAndFlush(savedList);
        return token;
    }

    @Transactional(readOnly = true)
    public SharedListView getSharedList(String token) {
        if (token == null || token.isBlank()) {
            throw new SharedListNotFoundException();
        }
        SavedList savedList = savedListRepository.findByShareToken(token)
                .filter(list -> !list.isEmpty())
                .orElseThrow(SharedListNotFoundException::new);
        List<SharedProductView> products = savedList.getItems().stream()
                .map(item -> {
                    Product product = item.getProduct();
                    var bestListing = priceComparisonService.bestListing(product, ProfileSettings.unrestricted());
                    return new SharedProductView(
                            product.getName(),
                            product.getBrand(),
                            product.getModel(),
                            product.getImageUrl(),
                            bestListing.map(listing -> listing.getRetailer().getName()).orElse(null),
                            bestListing.map(com.eecs3311.productfinder.domain.catalog.Listing::getPrice).orElse(null)
                    );
                })
                .toList();
        return new SharedListView(products);
    }

    @Transactional
    public SavedList addProduct(long userId, String modelName) {
        Objects.requireNonNull(modelName, "modelName must not be null");
        Product product = productRepository.findByModelIgnoreCase(modelName.trim())
                .orElseThrow(() -> new IllegalArgumentException("Product not found for model: " + modelName));
        SavedList savedList = findOrCreate(userId);
        savedList.addItem(product);
        return savedListRepository.saveAndFlush(savedList);
    }

    @Transactional
    public SavedList addProductById(long userId, long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));
        SavedList savedList = findOrCreate(userId);
        savedList.addItem(product);
        return savedListRepository.saveAndFlush(savedList);
    }

    @Transactional
    public SavedItem saveNote(long userId, long productId, String note) {
        SavedItem item = findSavedItem(userId, productId)
                .orElseThrow(() -> new SavedItemNotFoundException(productId));
        item.updateNote(note);
        savedListRepository.flush();
        return item;
    }

    @Transactional
    public SavedItem updateTrustLabel(long userId, long productId, String trustLabel) {
        SavedItem item = findSavedItem(userId, productId)
                .orElseThrow(() -> new SavedItemNotFoundException(productId));
        item.setTrustLabel(trustLabel);
        savedListRepository.flush();
        return item;
    }

    @Transactional
    public void saveRecommendationOffer(long userId, long productId, OfferSnapshot offer) {
        SavedItem item = findSavedItem(userId, productId)
                .orElseThrow(() -> new SavedItemNotFoundException(productId));
        item.setSavedOffer(offer);
        savedListRepository.flush();
    }

    @Transactional(readOnly = true)
    public Optional<SavedItem> findSavedItem(long userId, long productId) {
        return savedListRepository.findByUserId(userId)
                .flatMap(savedList -> savedList.getItems().stream()
                        .filter(item -> item.getProduct().getId() != null
                                && item.getProduct().getId() == productId)
                        .findFirst());
    }

    @Transactional
    public void removeProduct(long userId, long productId) {
        SavedList savedList = savedListRepository.findByUserId(userId)
                .orElseThrow(() -> new SavedItemNotFoundException(productId));
        try {
            savedList.removeItem(productId);
        } catch (IllegalArgumentException exception) {
            throw new SavedItemNotFoundException(productId);
        }
        savedListRepository.saveAndFlush(savedList);
    }

    private SavedList findOrCreate(long userId) {
        return savedListRepository.findByUserId(userId).orElseGet(() -> {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
            return new SavedList(user);
        });
    }

    private String newShareToken() {
        for (int attempt = 0; attempt < MAX_TOKEN_ATTEMPTS; attempt++) {
            byte[] randomBytes = new byte[SHARE_TOKEN_BYTES];
            SECURE_RANDOM.nextBytes(randomBytes);
            String token = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
            if (savedListRepository.findByShareToken(token).isEmpty()) {
                return token;
            }
        }
        throw new IllegalStateException("Unable to generate a unique share token");
    }

}
