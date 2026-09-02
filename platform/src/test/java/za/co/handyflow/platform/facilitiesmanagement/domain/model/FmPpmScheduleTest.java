package za.co.handyflow.platform.facilitiesmanagement.domain.model;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class FmPpmScheduleTest {

    @Test
    void createRejectsNonPositiveFrequency() {
        TenantId tenantId = TenantId.generate();
        UUID assetId = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class,
                () -> FmPpmSchedule.create(tenantId, assetId, "Filter change", null, 0, LocalDate.now()));
    }

    @Test
    void isDueReflectsNextDueDateAndActiveFlag() {
        FmPpmSchedule schedule = FmPpmSchedule.create(TenantId.generate(), UUID.randomUUID(),
                "Filter change", "Replace HVAC filters", 30, LocalDate.now().minusDays(1));
        assertTrue(schedule.isDue(LocalDate.now()));

        schedule.deactivate();
        assertFalse(schedule.isDue(LocalDate.now()));

        schedule.reactivate();
        assertTrue(schedule.isDue(LocalDate.now()));
    }

    @Test
    void recordCompletedAdvancesNextDueDateByFrequency() {
        FmPpmSchedule schedule = FmPpmSchedule.create(TenantId.generate(), UUID.randomUUID(),
                "Generator service", null, 180, LocalDate.now());
        LocalDate completedDate = LocalDate.now();
        schedule.recordCompleted(completedDate);
        assertEquals(completedDate, schedule.getLastCompletedDate());
        assertEquals(completedDate.plusDays(180), schedule.getNextDueDate());
    }

    @Test
    void softDeleteAlsoDeactivates() {
        FmPpmSchedule schedule = FmPpmSchedule.create(TenantId.generate(), UUID.randomUUID(),
                "Fire extinguisher check", null, 90, LocalDate.now());
        schedule.softDelete();
        assertTrue(schedule.isDeleted());
        assertFalse(schedule.isActive());
    }
}
