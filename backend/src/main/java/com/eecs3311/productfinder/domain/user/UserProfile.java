package com.eecs3311.productfinder.domain.user;

import com.eecs3311.productfinder.domain.catalog.ProductCondition;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

@Entity
@Table(name = "user_profile", uniqueConstraints = {
        @UniqueConstraint(name = "uk_user_profile_user", columnNames = "user_id")
})
public class UserProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(precision = 12, scale = 2)
    private BigDecimal budget;

    @ElementCollection
    @CollectionTable(name = "profile_excluded_brand", joinColumns = @JoinColumn(name = "profile_id"))
    @Column(name = "brand", nullable = false, length = 120)
    private Set<String> excludedBrands = new LinkedHashSet<>();

    @ElementCollection
    @CollectionTable(name = "profile_allowed_condition", joinColumns = @JoinColumn(name = "profile_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "condition_value", nullable = false, length = 40)
    private Set<ProductCondition> conditions = new LinkedHashSet<>();

    protected UserProfile() {
    }

    public UserProfile(User user, BigDecimal budget, Set<String> excludedBrands, Set<ProductCondition> conditions) {
        this.user = Objects.requireNonNull(user, "user must not be null");
        if (budget != null && budget.signum() < 0) {
            throw new IllegalArgumentException("budget must not be negative");
        }
        this.budget = budget;
        this.excludedBrands = new LinkedHashSet<>(
                Objects.requireNonNull(excludedBrands, "excludedBrands must not be null"));
        this.conditions = new LinkedHashSet<>(Objects.requireNonNull(conditions, "conditions must not be null"));
        user.assignProfile(this);
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public BigDecimal getBudget() {
        return budget;
    }

    public Set<String> getExcludedBrands() {
        return Set.copyOf(excludedBrands);
    }

    public Set<ProductCondition> getConditions() {
        return Set.copyOf(conditions);
    }

    public void updateSettings(BigDecimal budget, Set<String> excludedBrands, Set<ProductCondition> conditions) {
        if (budget != null && budget.signum() < 0) {
            throw new IllegalArgumentException("budget must not be negative");
        }
        this.budget = budget;
        this.excludedBrands = new LinkedHashSet<>(Objects.requireNonNull(excludedBrands,
                "excludedBrands must not be null"));
        this.conditions = new LinkedHashSet<>(Objects.requireNonNull(conditions, "conditions must not be null"));
    }
}
