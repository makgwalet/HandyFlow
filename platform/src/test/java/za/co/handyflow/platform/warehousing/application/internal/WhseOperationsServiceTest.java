package za.co.handyflow.platform.warehousing.application.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class WhseOperationsServiceTest {

    private final WhseOperationsService service = new WhseOperationsService(mock(JdbcTemplate.class));

    @Test
    @DisplayName("page size defaults to 50 and is capped at 200")
    void pageSizeIsBounded() {
        assertEquals(50, WhseOperationsService.clampSize(0));
        assertEquals(50, WhseOperationsService.clampSize(-5));
        assertEquals(25, WhseOperationsService.clampSize(25));
        assertEquals(200, WhseOperationsService.clampSize(100000));
    }

    @Test
    @DisplayName("the trend has one point for every day asked for, quiet days included, oldest first, and is capped at 90 days")
    void trendHasEveryDay() {
        TenantId t = TenantId.of(UUID.randomUUID());
        List<WhseOperationsService.TrendPoint> week = service.trend(t, 7);
        assertEquals(7, week.size());
        assertEquals(LocalDate.now(service.zone()), week.get(6).day());
        assertEquals(LocalDate.now(service.zone()).minusDays(6), week.get(0).day());
        assertEquals(0, week.get(3).received().signum());
        assertEquals(90, service.trend(t, 5000).size());
        assertEquals(1, service.trend(t, 0).size());
    }
}
