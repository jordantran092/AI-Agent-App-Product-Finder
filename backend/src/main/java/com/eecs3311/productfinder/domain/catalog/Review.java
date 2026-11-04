package com.eecs3311.productfinder.domain.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "review")
public class Review {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "retailer_id", nullable = false)
    private Retailer retailer;

    @Column(nullable = false, precision = 2, scale = 1)
    private BigDecimal rating;

    @Column(nullable = false, length = 4000)
    private String text;

    @Column(name = "reviewed_at", nullable = false)
    private OffsetDateTime reviewedAt;

    protected Review() {
    }

    public Review(Product product, Retailer retailer, BigDecimal rating, String text, OffsetDateTime reviewedAt) {
        this.product = product;
        this.retailer = retailer;
        this.rating = rating;
        this.text = text;
        this.reviewedAt = reviewedAt;
    }

    public Long getId() {
        return id;
    }

    public Product getProduct() {
        return product;
    }

    public Retailer getRetailer() {
        return retailer;
    }

    public BigDecimal getRating() {
        return rating;
    }

    public String getText() {
        return text;
    }

    public OffsetDateTime getReviewedAt() {
        return reviewedAt;
    }
}
