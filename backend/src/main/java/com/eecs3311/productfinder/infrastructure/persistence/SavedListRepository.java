package com.eecs3311.productfinder.infrastructure.persistence;

import com.eecs3311.productfinder.domain.savedlist.SavedList;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SavedListRepository extends JpaRepository<SavedList, Long> {

    @EntityGraph(attributePaths = {"items", "items.product"})
    Optional<SavedList> findByUserId(Long userId);

    Optional<SavedList> findByShareToken(String shareToken);
}
