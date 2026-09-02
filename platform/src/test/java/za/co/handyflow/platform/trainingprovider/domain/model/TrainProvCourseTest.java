package za.co.handyflow.platform.trainingprovider.domain.model;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class TrainProvCourseTest {

    private TrainProvCourse newCourse() {
        return TrainProvCourse.create(TenantId.generate(), "CRS-00001", "Advanced Rigging", "desc",
                "US12345", 4, 10, new BigDecimal("3"), new BigDecimal("1500.00"), true, 24);
    }

    @Test
    void newCourseStartsActive() {
        TrainProvCourse course = newCourse();
        assertEquals("ACTIVE", course.getStatus());
        assertTrue(course.isActive());
    }

    @Test
    void rejectsNegativePrice() {
        TenantId tenantId = TenantId.generate();
        assertThrows(IllegalArgumentException.class, () -> TrainProvCourse.create(tenantId, "CRS-00002", "Bad Course",
                null, null, null, null, null, new BigDecimal("-1"), false, null));
    }

    @Test
    void archiveAndReactivateToggleStatus() {
        TrainProvCourse course = newCourse();
        course.archive();
        assertEquals("ARCHIVED", course.getStatus());
        assertFalse(course.isActive());
        course.reactivate();
        assertEquals("ACTIVE", course.getStatus());
    }

    @Test
    void archivingAlreadyArchivedThrows() {
        TrainProvCourse course = newCourse();
        course.archive();
        assertThrows(IllegalStateException.class, course::archive);
    }

    @Test
    void softDeleteSetsDeletedAt() {
        TrainProvCourse course = newCourse();
        course.softDelete();
        assertTrue(course.isDeleted());
        assertFalse(course.isActive());
    }
}
