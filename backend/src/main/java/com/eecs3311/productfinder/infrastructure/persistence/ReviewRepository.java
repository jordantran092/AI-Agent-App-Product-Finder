package com.eecs3311.productfinder.infrastructure.persistence;

import com.eecs3311.productfinder.domain.catalog.Review;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    List<Review> findByProductIdOrderByReviewedAtDesc(Long productId);
}
