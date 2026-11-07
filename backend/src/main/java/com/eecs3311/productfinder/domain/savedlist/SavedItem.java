package com.eecs3311.productfinder.domain.savedlist;

import com.eecs3311.productfinder.domain.catalog.Product;
import com.eecs3311.productfinder.domain.catalog.OfferSnapshot;
import com.eecs3311.productfinder.domain.catalog.ProductCondition;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.math.BigDecimal;

@Entity
@Table(name = "saved_item", uniqueConstraints = {
        @UniqueConstraint(name = "uk_saved_item_list_product", columnNames = {"saved_list_id", "product_id"})
})
public class SavedItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "saved_list_id", nullable = false)
    private SavedList savedList;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(length = 4000)
    private String note;

    @Column(name = "trust_label", length = 200)
    private String trustLabel;

    @Column(name = "saved_offer_retailer", length = 120)
    private String savedOfferRetailer;

    @Column(name = "saved_offer_price", precision = 12, scale = 2)
    private BigDecimal savedOfferPrice;

    @Enumerated(EnumType.STRING)
    @Column(name = "saved_offer_condition", length = 40)
    private ProductCondition savedOfferCondition;

    protected SavedItem() {
    }

    SavedItem(SavedList savedList, Product product) {
        this.savedList = savedList;
        this.product = product;
    }

    public Long getId() {
        return id;
    }

    public SavedList getSavedList() {
        return savedList;
    }

    public Product getProduct() {
        return product;
    }

    public String getNote() {
        return note;
    }

    public String getTrustLabel() {
        return trustLabel;
    }

    public OfferSnapshot getSavedOffer() {
        return savedOfferRetailer == null ? null
                : new OfferSnapshot(savedOfferRetailer, savedOfferPrice, savedOfferCondition);
    }

    public void setSavedOffer(OfferSnapshot offer) {
        java.util.Objects.requireNonNull(offer, "offer must not be null");
        savedOfferRetailer = offer.retailer();
        savedOfferPrice = offer.price();
        savedOfferCondition = offer.condition();
    }

    public void updateNote(String note) {
        if (note != null && note.length() > 4000) {
            throw new IllegalArgumentException("note must not exceed 4000 characters");
        }
        this.note = note;
    }

    public void setTrustLabel(String trustLabel) {
        this.trustLabel = trustLabel;
    }
}
