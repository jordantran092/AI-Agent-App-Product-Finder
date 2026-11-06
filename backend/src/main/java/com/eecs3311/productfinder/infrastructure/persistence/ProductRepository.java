package com.eecs3311.productfinder.infrastructure.persistence;

import com.eecs3311.productfinder.domain.catalog.Product;
import com.eecs3311.productfinder.domain.catalog.ProductCategory;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {

    Optional<Product> findByModelIgnoreCase(String model);

    List<Product> findByNameIgnoreCaseOrderByIdAsc(String name);

    boolean existsByBrandIgnoreCase(String brand);

    List<Product> findByCategory(ProductCategory category);

    @EntityGraph(attributePaths = {"listings", "listings.retailer"})
    List<Product> findWithListingsByCategory(ProductCategory category);

    @EntityGraph(attributePaths = {"listings", "listings.retailer"})
    @Query("select p from Product p where p.id = :id")
    Optional<Product> findProductWithListingsById(@Param("id") Long id);
}
