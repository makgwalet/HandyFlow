package za.co.handyflow.platform.facilities.domain.model;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class FacilityPpmScheduleTest {

    @Test
    void createRejectsNonPositiveFrequency() {
        TenantId tenantId = TenantId.generate();
        UUID assetId = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class,
                () -> FacilityPpmSchedule.create(tenantId, assetId, "Filter change", null, 0, LocalDate.now()));
    }

    @Test
    void isDueReflectsNextDueDateAndActiveFlag() {
        FacilityPpmSchedule schedule = FacilityPpmSchedule.create(TenantId.generate(), UUID.randomUUID(),
                "Filter change", "Replace HVAC filters", 30, LocalDate.now().minusDays(1));
        assertTrue(schedule.isDue(LocalDate.now()));

        schedule.deactivate();
        assertFalse(schedule.isDue(LocalDate.now()));
    }

    @Test
    void recordCompletedAdvancesNextDueDateByFrequency() {
        FacilityPpmSchedule schedule = FacilityPpmSchedule.create(TenantId.generate(), UUID.randomUUID(),
                "Generator service", null, 180, LocalDate.now());
        LocalDate completedDate = LocalDate.now();
        schedule.recordCompleted(completedDate);
        assertEquals(completedDate, schedule.getLastCompletedDate());
        assertEquals(completedDate.plusDays(180), schedule.getNextDueDate());
    }
}
