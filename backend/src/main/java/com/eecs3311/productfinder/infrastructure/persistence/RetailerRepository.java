package com.eecs3311.productfinder.infrastructure.persistence;

import com.eecs3311.productfinder.domain.catalog.Retailer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RetailerRepository extends JpaRepository<Retailer, Long> {

    Optional<Retailer> findByNameIgnoreCase(String name);
}
