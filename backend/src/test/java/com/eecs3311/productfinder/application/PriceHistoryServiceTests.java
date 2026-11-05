package com.eecs3311.productfinder.application;

import com.eecs3311.productfinder.domain.catalog.PricePoint;
import com.eecs3311.productfinder.infrastructure.persistence.PricePointRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PriceHistoryServiceTests {

    @Test
    void computesAverageMinimumAndCurrentDifferenceFromHistory() {
        PricePointRepository repository = mock(PricePointRepository.class);
        when(repository.findByListingIdOrderByRecordedAtAsc(7L)).thenReturn(List.of(
                point("100.00", 1),
                point("80.00", 2),
                point("90.00", 3)
        ));
        PriceHistoryService service = new PriceHistoryService(repository);

        PriceHistoryStats stats = service.computeStats(7, new BigDecimal("95.00")).orElseThrow();

        assertThat(stats.averagePrice()).isEqualByComparingTo("90.00");
        assertThat(stats.lowestPrice()).isEqualByComparingTo("80.00");
        assertThat(stats.currentPrice()).isEqualByComparingTo("95.00");
        assertThat(stats.differenceFromAverage()).isEqualByComparingTo("5.00");
    }

    @Test
    void usesLatestHistoryPriceWhenListingHasNoCurrentPriceAndReturnsEmptyWithoutHistory() {
        PricePointRepository repository = mock(PricePointRepository.class);
        when(repository.findByListingIdOrderByRecordedAtAsc(8L)).thenReturn(List.of(point("123.45", 1)));
        when(repository.findByListingIdOrderByRecordedAtAsc(9L)).thenReturn(List.of());
        PriceHistoryService service = new PriceHistoryService(repository);

        assertThat(service.computeStats(8, null).orElseThrow().currentPrice()).isEqualByComparingTo("123.45");
        assertThat(service.computeStats(9, null)).isEmpty();
    }

    @Test
    void rejectsNonpositiveListingIds() {
        PriceHistoryService service = new PriceHistoryService(mock(PricePointRepository.class));

        assertThatIllegalArgumentException().isThrownBy(() -> service.computeStats(0, BigDecimal.TEN));
    }

    private PricePoint point(String price, int day) {
        return new PricePoint(
                null,
                OffsetDateTime.parse("2026-01-0" + day + "T00:00:00Z"),
                new BigDecimal(price)
        );
    }
}
