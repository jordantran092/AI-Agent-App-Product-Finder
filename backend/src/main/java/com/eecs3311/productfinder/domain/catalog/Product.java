package com.eecs3311.productfinder.domain.catalog;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Entity
@Table(name = "product", uniqueConstraints = {
        @UniqueConstraint(name = "uk_product_model", columnNames = "model")
})
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(nullable = false, length = 120)
    private String brand;

    @Column(nullable = false, length = 120)
    private String model;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private ProductCategory category;

    @Column(nullable = false, length = 2000)
    private String description;

    @Column(name = "image_url", length = 1000)
    private String imageUrl;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "json")
    private Map<String, Object> attributes = new LinkedHashMap<>();

    @Column(nullable = false)
    private boolean discontinued;

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Listing> listings = new ArrayList<>();

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Review> reviews = new ArrayList<>();

    protected Product() {
    }

    public Product(
            String name,
            String brand,
            String model,
            ProductCategory category,
            String description,
            String imageUrl,
            Map<String, Object> attributes
    ) {
        this.name = name;
        this.brand = brand;
        this.model = model;
        this.category = category;
        this.description = description;
        this.imageUrl = imageUrl;
        this.attributes = new LinkedHashMap<>(attributes);
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getBrand() {
        return brand;
    }

    public String getModel() {
        return model;
    }

    public ProductCategory getCategory() {
        return category;
    }

    public String getDescription() {
        return description;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public Map<String, Object> getAttributes() {
        return Map.copyOf(attributes);
    }

    public boolean isDiscontinued() {
        return discontinued;
    }

    public List<Listing> getListings() {
        return List.copyOf(listings);
    }

    public List<Review> getReviews() {
        return List.copyOf(reviews);
    }
}
