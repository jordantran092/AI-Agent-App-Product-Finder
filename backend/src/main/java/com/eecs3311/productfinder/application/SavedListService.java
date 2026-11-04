package com.eecs3311.productfinder.application;

import com.eecs3311.productfinder.domain.catalog.Product;
import com.eecs3311.productfinder.domain.savedlist.SavedList;
import com.eecs3311.productfinder.domain.user.User;
import com.eecs3311.productfinder.infrastructure.persistence.ProductRepository;
import com.eecs3311.productfinder.infrastructure.persistence.SavedListRepository;
import com.eecs3311.productfinder.infrastructure.persistence.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Optional;

@Service
public class SavedListService {

    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final SavedListRepository savedListRepository;

    public SavedListService(UserRepository userRepository, ProductRepository productRepository,
                            SavedListRepository savedListRepository) {
        this.userRepository = userRepository;
        this.productRepository = productRepository;
        this.savedListRepository = savedListRepository;
    }

    @Transactional(readOnly = true)
    public Optional<SavedList> get(long userId) {
        return savedListRepository.findByUserId(userId);
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
    public void removeProduct(long userId, long productId) {
        SavedList savedList = savedListRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("Saved list not found for user: " + userId));
        savedList.removeItem(productId);
        savedListRepository.saveAndFlush(savedList);
    }

    private SavedList findOrCreate(long userId) {
        return savedListRepository.findByUserId(userId).orElseGet(() -> {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
            return new SavedList(user);
        });
    }

}
