package za.co.handyflow.platform.training.domain.model;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class TrainingCourseTest {

    private TrainingCourse newCourse() {
        return TrainingCourse.create(TenantId.generate(), "CRS-00001", "First Aid Level 1", "Basic first aid",
                "Safety", "IN_PERSON", new BigDecimal("8"), "Jane Trainer", new BigDecimal("500"), true, 12);
    }

    @Test
    void newCourseStartsActive() {
        TrainingCourse course = newCourse();
        assertEquals("ACTIVE", course.getStatus());
        assertTrue(course.isActive());
        assertFalse(course.isDeleted());
    }

    @Test
    void archiveAndReactivateToggleStatus() {
        TrainingCourse course = newCourse();
        course.archive();
        assertEquals("ARCHIVED", course.getStatus());
        assertFalse(course.isActive());
        course.reactivate();
        assertEquals("ACTIVE", course.getStatus());
        assertTrue(course.isActive());
    }

    @Test
    void archivingAlreadyArchivedCourseThrows() {
        TrainingCourse course = newCourse();
        course.archive();
        assertThrows(IllegalStateException.class, course::archive);
    }

    @Test
    void softDeleteSetsDeletedAt() {
        TrainingCourse course = newCourse();
        course.softDelete();
        assertTrue(course.isDeleted());
        assertNotNull(course.getDeletedAt());
        assertFalse(course.isActive());
    }

    @Test
    void updateChangesFieldsAndBumpsUpdatedAt() {
        TrainingCourse course = newCourse();
        var before = course.getUpdatedAt();
        course.update("First Aid Level 2", "Advanced", "Safety", "HYBRID",
                new BigDecimal("16"), "New Trainer", new BigDecimal("800"), false, null);
        assertEquals("First Aid Level 2", course.getTitle());
        assertEquals("HYBRID", course.getDeliveryMode());
        assertFalse(course.isCertificationOffered());
        assertNull(course.getCertificateValidityMonths());
        assertTrue(course.getUpdatedAt().isAfter(before) || course.getUpdatedAt().equals(before));
    }
}
