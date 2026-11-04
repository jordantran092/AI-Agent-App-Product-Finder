package com.eecs3311.productfinder.domain.catalog;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "listing", uniqueConstraints = {
        @UniqueConstraint(name = "uk_listing_product_retailer", columnNames = {"product_id", "retailer_id"})
})
public class Listing {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "retailer_id", nullable = false)
    private Retailer retailer;

    @Column(precision = 12, scale = 2)
    private BigDecimal price;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private ProductCondition condition;

    @Column(name = "in_stock", nullable = false)
    private boolean inStock;

    @OneToMany(mappedBy = "listing", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PricePoint> priceHistory = new ArrayList<>();

    protected Listing() {
    }

    public Listing(Product product, Retailer retailer, BigDecimal price, ProductCondition condition, boolean inStock) {
        this.product = product;
        this.retailer = retailer;
        this.price = price;
        this.condition = condition;
        this.inStock = inStock;
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

    public BigDecimal getPrice() {
        return price;
    }

    public ProductCondition getCondition() {
        return condition;
    }

    public boolean isInStock() {
        return inStock;
    }

    public List<PricePoint> getPriceHistory() {
        return List.copyOf(priceHistory);
    }
}
