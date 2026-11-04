package com.eecs3311.productfinder.domain.user;

import com.eecs3311.productfinder.domain.savedlist.SavedList;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.OffsetDateTime;
import java.util.Objects;

@Entity
@Table(name = "app_user", uniqueConstraints = {
        @UniqueConstraint(name = "uk_app_user_username", columnNames = "username")
})
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 80)
    private String username;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @OneToOne(mappedBy = "user")
    private UserProfile profile;

    @OneToOne(mappedBy = "user")
    private SavedList savedList;

    protected User() {
    }

    public User(String username, String passwordHash) {
        this.username = Objects.requireNonNull(username, "username must not be null");
        this.passwordHash = Objects.requireNonNull(passwordHash, "passwordHash must not be null");
        if (username.isBlank() || passwordHash.isBlank()) {
            throw new IllegalArgumentException("username and passwordHash must not be blank");
        }
        this.createdAt = OffsetDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public UserProfile getProfile() {
        return profile;
    }

    public SavedList getSavedList() {
        return savedList;
    }

    public void assignProfile(UserProfile profile) {
        Objects.requireNonNull(profile, "profile must not be null");
        if (this.profile != null && this.profile != profile) {
            throw new IllegalStateException("User already has a profile");
        }
        this.profile = profile;
    }

    public void assignSavedList(SavedList savedList) {
        Objects.requireNonNull(savedList, "savedList must not be null");
        if (this.savedList != null && this.savedList != savedList) {
            throw new IllegalStateException("User already has a saved list");
        }
        this.savedList = savedList;
    }
}
