package com.eecs3311.productfinder.application;

import com.eecs3311.productfinder.domain.catalog.PricePoint;
import com.eecs3311.productfinder.infrastructure.persistence.PricePointRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;

@Service
public class PriceHistoryService {

    private final PricePointRepository pricePointRepository;

    public PriceHistoryService(PricePointRepository pricePointRepository) {
        this.pricePointRepository = pricePointRepository;
    }

    @Transactional(readOnly = true)
    public Optional<PriceHistoryStats> computeStats(long listingId, BigDecimal currentPrice) {
        if (listingId <= 0) {
            throw new IllegalArgumentException("listingId must be positive");
        }
        List<PricePoint> history = pricePointRepository.findByListingIdOrderByRecordedAtAsc(listingId);
        if (history.isEmpty()) {
            return Optional.empty();
        }
        BigDecimal sum = history.stream()
                .map(PricePoint::getPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal average = sum.divide(BigDecimal.valueOf(history.size()), 2, RoundingMode.HALF_UP);
        BigDecimal lowest = history.stream()
                .map(PricePoint::getPrice)
                .min(BigDecimal::compareTo)
                .orElseThrow();
        BigDecimal effectiveCurrentPrice = currentPrice == null
                ? history.getLast().getPrice()
                : currentPrice;
        return Optional.of(new PriceHistoryStats(
                average,
                lowest,
                effectiveCurrentPrice,
                effectiveCurrentPrice.subtract(average).setScale(2, RoundingMode.HALF_UP)
        ));
    }
}
