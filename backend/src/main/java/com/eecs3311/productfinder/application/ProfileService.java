package com.eecs3311.productfinder.application;

import com.eecs3311.productfinder.domain.catalog.ProductCondition;
import com.eecs3311.productfinder.domain.user.ProfileValidationException;
import com.eecs3311.productfinder.domain.user.User;
import com.eecs3311.productfinder.domain.user.UserProfile;
import com.eecs3311.productfinder.infrastructure.persistence.UserProfileRepository;
import com.eecs3311.productfinder.infrastructure.persistence.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

@Service
public class ProfileService {

    private final UserRepository userRepository;
    private final UserProfileRepository profileRepository;

    public ProfileService(UserRepository userRepository, UserProfileRepository profileRepository) {
        this.userRepository = userRepository;
        this.profileRepository = profileRepository;
    }

    @Transactional(readOnly = true)
    public ProfileSettings get(long userId) {
        return profileRepository.findByUserId(userId)
                .map(profile -> new ProfileSettings(profile.getBudget(), profile.getExcludedBrands(),
                        profile.getConditions()))
                .orElseGet(ProfileSettings::unrestricted);
    }

    @Transactional
    public ProfileSettings update(long userId, BigDecimal budget, Set<String> excludedBrands,
                                  Set<ProductCondition> conditions) {
        ProfileSettings settings = validate(budget, excludedBrands, conditions);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        UserProfile profile = profileRepository.findByUserId(userId)
                .map(existing -> {
                    existing.updateSettings(settings.budget(), settings.excludedBrands(), settings.conditions());
                    return existing;
                })
                .orElseGet(() -> new UserProfile(user, settings.budget(), settings.excludedBrands(),
                        settings.conditions()));

        profileRepository.saveAndFlush(profile);
        return settings;
    }

    public ProfileSettings validate(BigDecimal budget, Set<String> excludedBrands,
                                    Set<ProductCondition> conditions) {
        Objects.requireNonNull(excludedBrands, "excludedBrands must not be null");
        Objects.requireNonNull(conditions, "conditions must not be null");
        List<String> violations = new ArrayList<>();
        if (budget != null) {
            if (budget.signum() < 0) {
                violations.add("budget must not be negative");
            }
            if (budget.scale() > 2 || budget.precision() > 12) {
                violations.add("budget must fit NUMERIC(12,2)");
            }
        }

        Set<String> normalizedBrands = new LinkedHashSet<>();
        for (String brand : excludedBrands) {
            if (brand == null || brand.isBlank()) {
                violations.add("excluded brands must not be blank");
            } else if (brand.trim().length() > 120) {
                violations.add("excluded brand names must not exceed 120 characters");
            } else {
                normalizedBrands.add(brand.trim().toLowerCase(Locale.ROOT));
            }
        }
        if (conditions.stream().anyMatch(Objects::isNull)) {
            violations.add("allowed conditions must not contain null");
        }
        if (!violations.isEmpty()) {
            throw new ProfileValidationException(violations);
        }
        return new ProfileSettings(budget, normalizedBrands, conditions);
    }
}
