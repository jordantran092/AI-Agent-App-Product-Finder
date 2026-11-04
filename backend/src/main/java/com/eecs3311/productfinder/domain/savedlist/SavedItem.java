package com.eecs3311.productfinder.domain.savedlist;

import com.eecs3311.productfinder.domain.catalog.Product;
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

    public void updateNote(String note) {
        this.note = note;
    }

    public void setTrustLabel(String trustLabel) {
        this.trustLabel = trustLabel;
    }
}
