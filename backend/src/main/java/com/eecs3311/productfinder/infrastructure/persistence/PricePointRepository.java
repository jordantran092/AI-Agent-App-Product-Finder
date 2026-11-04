package com.eecs3311.productfinder.infrastructure.persistence;

import com.eecs3311.productfinder.domain.catalog.PricePoint;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PricePointRepository extends JpaRepository<PricePoint, Long> {

    List<PricePoint> findByListingIdOrderByRecordedAtAsc(Long listingId);
}
