package com.eecs3311.productfinder.infrastructure.persistence;

import com.eecs3311.productfinder.domain.savedlist.SavedList;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SavedListRepository extends JpaRepository<SavedList, Long> {

    Optional<SavedList> findByUserId(Long userId);

    Optional<SavedList> findByShareToken(String shareToken);
}
