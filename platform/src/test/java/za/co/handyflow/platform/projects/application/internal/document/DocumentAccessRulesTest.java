package za.co.handyflow.platform.projects.application.internal.document;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class DocumentAccessRulesTest {

    @Test
    void void_isForControllersOrTheUploader() {
        assertTrue(DocumentAccessRules.mayVoid(true, false));
        assertTrue(DocumentAccessRules.mayVoid(false, true));
        assertFalse(DocumentAccessRules.mayVoid(false, false));
    }

    @Test
    void visibilityAndLegalHold_areForControllersOnly() {
        assertTrue(DocumentAccessRules.mayChangeVisibility(true));
        assertFalse(DocumentAccessRules.mayChangeVisibility(false));
        assertTrue(DocumentAccessRules.mayChangeLegalHold(true));
        assertFalse(DocumentAccessRules.mayChangeLegalHold(false));
    }

    @Test
    void retentionRunsFromTheClosureDate() {
        assertEquals(LocalDate.of(2033, 10, 14), DocumentAccessRules.retentionUntil(LocalDate.of(2026, 10, 14), 7));
        assertEquals(LocalDate.of(2029, 2, 28), DocumentAccessRules.retentionUntil(LocalDate.of(2024, 2, 29), 5));
        assertEquals(LocalDate.of(2026, 10, 14), DocumentAccessRules.retentionUntil(LocalDate.of(2026, 10, 14), -3));
    }

    @Test
    void dueForReview_onlyAfterTheDate_andNeverUnderLegalHold() {
        LocalDate today = LocalDate.of(2026, 11, 3);
        assertTrue(DocumentAccessRules.dueForRetentionReview(today.minusDays(1), false, today));
        assertFalse(DocumentAccessRules.dueForRetentionReview(today, false, today));
        assertFalse(DocumentAccessRules.dueForRetentionReview(today.plusDays(1), false, today));
        assertFalse(DocumentAccessRules.dueForRetentionReview(today.minusYears(5), true, today));
        assertFalse(DocumentAccessRules.dueForRetentionReview(null, false, today));
    }
}
