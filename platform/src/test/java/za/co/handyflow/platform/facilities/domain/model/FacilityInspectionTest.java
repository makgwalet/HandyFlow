package za.co.handyflow.platform.facilities.domain.model;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.facilities.domain.model.FacilityInspectionTemplate.ItemSpec;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class FacilityInspectionTest {

    private final TenantId tenant = TenantId.generate();

    private FacilityInspectionTemplate template() {
        return FacilityInspectionTemplate.create(tenant, "Monthly fire check", "desc", "fire",
                List.of(new ItemSpec("Extinguisher pressure in green", true), new ItemSpec("Signage visible", false),
                        new ItemSpec("Access clear", false)));
    }

    private FacilityInspection started() {
        return FacilityInspection.start(tenant, template(), UUID.randomUUID(), null, UUID.randomUUID(), "Sipho");
    }

    private UUID itemId(FacilityInspection i, int idx) { return i.getResults().get(idx).getItemId(); }

    // ── template ─────────────────────────────────────────────────────────────

    @Test
    void templateKeepsItemOrderAndNormalisesTheCategory() {
        FacilityInspectionTemplate t = template();
        assertEquals(List.of(1, 2, 3), t.getItems().stream().map(FacilityInspectionTemplateItem::getPosition).toList());
        assertEquals("Signage visible", t.getItems().get(1).getPrompt());
        assertTrue(t.getItems().get(0).isCritical());
        assertEquals("FIRE", t.getAssetCategory());
        assertEquals(1, t.getRevision());
    }

    @Test
    void templateNeedsANameAndAtLeastOneCheck() {
        assertThrows(IllegalArgumentException.class, () -> FacilityInspectionTemplate.create(tenant, " ", null, null, List.of(new ItemSpec("x", false))));
        assertThrows(IllegalArgumentException.class, () -> FacilityInspectionTemplate.create(tenant, "N", null, null, List.of()));
        assertThrows(IllegalArgumentException.class, () -> FacilityInspectionTemplate.create(tenant, "N", null, null, List.of(new ItemSpec(" ", false))));
        assertThrows(IllegalArgumentException.class, () -> FacilityInspectionTemplate.create(tenant, "N", null, null,
                List.of(new ItemSpec("x".repeat(301), false))));
    }

    @Test
    void editingReplacesChecksAndBumpsTheRevisionWithoutTouchingStartedInspections() {
        FacilityInspectionTemplate t = template();
        FacilityInspection i = FacilityInspection.start(tenant, t, UUID.randomUUID(), null, UUID.randomUUID(), "Sipho");
        t.update("Renamed", null, null, List.of(new ItemSpec("Only one now", false)));
        assertEquals(2, t.getRevision());
        assertEquals(1, t.getItems().size());
        assertEquals(3, i.getResults().size());
        assertEquals("Monthly fire check", i.getTemplateName());
        assertEquals(1, i.getTemplateRevision());
    }

    @Test
    void aSwitchedOffTemplateCannotBeStarted() {
        FacilityInspectionTemplate t = template();
        t.setActive(false);
        assertThrows(IllegalStateException.class, () -> FacilityInspection.start(tenant, t, UUID.randomUUID(), null, null, "x"));
    }

    // ── answering and completing ─────────────────────────────────────────────

    @Test
    void aNewInspectionHasEveryCheckUnanswered() {
        FacilityInspection i = started();
        assertEquals("IN_PROGRESS", i.getStatus());
        assertEquals(3, i.unanswered().size());
    }

    @Test
    void cannotCompleteWhileChecksAreUnanswered() {
        FacilityInspection i = started();
        i.answer(itemId(i, 0), "PASS", null);
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> i.complete(null));
        assertTrue(e.getMessage().startsWith("2 check(s)"));
        assertEquals("IN_PROGRESS", i.getStatus());
    }

    @Test
    void allPassesGiveAPass() {
        FacilityInspection i = started();
        i.answer(itemId(i, 0), "pass", null);
        i.answer(itemId(i, 1), "NA", null);
        i.answer(itemId(i, 2), "PASS", "  ok ");
        i.complete("  all good ");
        assertEquals("COMPLETED", i.getStatus());
        assertEquals("PASS", i.getResult());
        assertEquals("all good", i.getNotes());
        assertNotNull(i.getCompletedAt());
        assertTrue(i.failures().isEmpty());
    }

    @Test
    void aFailedCheckNeedsACommentAndFailsTheInspection() {
        FacilityInspection i = started();
        assertThrows(IllegalArgumentException.class, () -> i.answer(itemId(i, 1), "FAIL", "  "));
        i.answer(itemId(i, 1), "FAIL", "Sign missing");
        i.answer(itemId(i, 0), "PASS", null);
        i.answer(itemId(i, 2), "PASS", null);
        i.complete(null);
        assertEquals("FAIL", i.getResult());
        assertEquals(1, i.failures().size());
        assertFalse(i.hasCriticalFailure());
    }

    @Test
    void aFailedCriticalCheckIsFlagged() {
        FacilityInspection i = started();
        i.answer(itemId(i, 0), "FAIL", "Pressure low");
        assertTrue(i.hasCriticalFailure());
    }

    @Test
    void anAnswerCanBeChangedBeforeCompletion() {
        FacilityInspection i = started();
        i.answer(itemId(i, 0), "FAIL", "Low");
        i.answer(itemId(i, 0), "PASS", null);
        assertEquals("PASS", i.getResults().get(0).getOutcome());
        assertNull(i.getResults().get(0).getComment());
    }

    @Test
    void badOutcomesAndUnknownChecksAreRejected() {
        FacilityInspection i = started();
        assertThrows(IllegalArgumentException.class, () -> i.answer(itemId(i, 0), "MAYBE", null));
        assertThrows(IllegalArgumentException.class, () -> i.answer(itemId(i, 0), null, null));
        assertThrows(IllegalArgumentException.class, () -> i.answer(UUID.randomUUID(), "PASS", null));
        assertThrows(IllegalArgumentException.class, () -> i.answer(itemId(i, 0), "PASS", "x".repeat(1001)));
    }

    @Test
    void aFinishedInspectionIsFrozen() {
        FacilityInspection i = started();
        for (int n = 0; n < 3; n++) i.answer(itemId(i, n), "PASS", null);
        i.complete(null);
        assertThrows(IllegalStateException.class, () -> i.answer(itemId(i, 0), "FAIL", "late"));
        assertThrows(IllegalStateException.class, () -> i.complete(null));
        assertThrows(IllegalStateException.class, () -> i.cancel("why"));
    }

    @Test
    void cancellingNeedsAReason() {
        FacilityInspection i = started();
        assertThrows(IllegalArgumentException.class, () -> i.cancel(" "));
        i.cancel(" Site closed ");
        assertEquals("CANCELLED", i.getStatus());
        assertEquals("Site closed", i.getCancelReason());
    }
}
