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
@Table(name = "price_point")
public class PricePoint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "listing_id", nullable = false)
    private Listing listing;

    @Column(name = "recorded_at", nullable = false)
    private OffsetDateTime recordedAt;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    protected PricePoint() {
    }

    public PricePoint(Listing listing, OffsetDateTime recordedAt, BigDecimal price) {
        this.listing = listing;
        this.recordedAt = recordedAt;
        this.price = price;
    }

    public Long getId() {
        return id;
    }

    public Listing getListing() {
        return listing;
    }

    public OffsetDateTime getRecordedAt() {
        return recordedAt;
    }

    public BigDecimal getPrice() {
        return price;
    }
}
