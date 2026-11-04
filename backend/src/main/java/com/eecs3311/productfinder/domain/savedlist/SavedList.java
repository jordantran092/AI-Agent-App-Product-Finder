package com.eecs3311.productfinder.domain.savedlist;

import com.eecs3311.productfinder.domain.catalog.Product;
import com.eecs3311.productfinder.domain.user.User;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Entity
@Table(name = "saved_list", uniqueConstraints = {
        @UniqueConstraint(name = "uk_saved_list_user", columnNames = "user_id"),
        @UniqueConstraint(name = "uk_saved_list_share_token", columnNames = "share_token")
})
public class SavedList {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "share_token", length = 128)
    private String shareToken;

    @OneToMany(mappedBy = "savedList", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<SavedItem> items = new ArrayList<>();

    protected SavedList() {
    }

    public SavedList(User user) {
        this.user = Objects.requireNonNull(user, "user must not be null");
        user.assignSavedList(this);
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public String getShareToken() {
        return shareToken;
    }

    public List<SavedItem> getItems() {
        return List.copyOf(items);
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }

    public void addItem(Product product) {
        Objects.requireNonNull(product, "product must not be null");
        boolean alreadySaved = items.stream().anyMatch(item -> sameProduct(item.getProduct(), product));
        if (alreadySaved) {
            throw new IllegalStateException("Product is already in the saved list");
        }
        items.add(new SavedItem(this, product));
    }

    public void removeItem(long productId) {
        boolean removed = items.removeIf(item -> item.getProduct().getId() != null
                && item.getProduct().getId() == productId);
        removeItemResult(removed);
    }

    public void removeItem(Product product) {
        Objects.requireNonNull(product, "product must not be null");
        boolean removed = items.removeIf(item -> sameProduct(item.getProduct(), product));
        removeItemResult(removed);
    }

    private boolean sameProduct(Product first, Product second) {
        Long firstId = first.getId();
        Long secondId = second.getId();
        return firstId != null && secondId != null
                ? firstId.equals(secondId)
                : first.getModel().equalsIgnoreCase(second.getModel());
    }

    private void removeItemResult(boolean removed) {
        if (!removed) {
            throw new IllegalArgumentException("Product is not in the saved list");
        }
        if (items.isEmpty()) {
            shareToken = null;
        }
    }

    public void setShareToken(String shareToken) {
        if (isEmpty() && shareToken != null) {
            throw new IllegalStateException("An empty saved list cannot be shared");
        }
        if (shareToken != null && shareToken.isBlank()) {
            throw new IllegalArgumentException("shareToken must not be blank");
        }
        this.shareToken = shareToken;
    }
}
