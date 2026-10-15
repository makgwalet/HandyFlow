package za.co.handyflow.platform.internalaudit.domain;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.internalaudit.application.internal.ControlLibrary;
import za.co.handyflow.platform.internalaudit.domain.model.AuditProgrammeStep;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AuditProgrammeStepTest {
    UUID who = UUID.randomUUID();

    AuditProgrammeStep step() { return AuditProgrammeStep.from(UUID.randomUUID(), UUID.randomUUID(), ControlLibrary.find("IT-01").get(), who); }

    @Test
    void copiesTheWordingFromTheLibrary() {
        var s = step();
        assertEquals("IT-01", s.getControlCode());
        assertEquals("NOT_STARTED", s.getStatus());
        assertTrue(s.getProcedure().startsWith("Compare the user list"));
        assertEquals("ISO/IEC 27001:2022 A.5.15, A.5.18", s.getReference());
        assertNull(AuditProgrammeStep.from(UUID.randomUUID(), UUID.randomUUID(), ControlLibrary.find("IT-12").get(), who).getReference());
    }

    @Test
    void completingNeedsAConclusionAndANote() {
        var s = step();
        assertThrows(IllegalArgumentException.class, () -> s.record("COMPLETED", null, "Reviewed the user lists.", who, "T"));
        assertThrows(IllegalArgumentException.class, () -> s.record("COMPLETED", "FINE", "Reviewed the user lists.", who, "T"));
        assertThrows(IllegalArgumentException.class, () -> s.record("COMPLETED", "EFFECTIVE", "short", who, "T"));
        s.record("completed", "effective", "  Reviewed the user lists.  ", who, "T");
        assertEquals("COMPLETED", s.getStatus());
        assertEquals("EFFECTIVE", s.getConclusion());
        assertEquals("Reviewed the user lists.", s.getNote());
        assertFalse(s.needsFinding());
    }

    @Test
    void notApplicableNeedsAReasonAndNoConclusion() {
        var s = step();
        assertThrows(IllegalArgumentException.class, () -> s.record("NOT_APPLICABLE", null, "no", who, "T"));
        assertThrows(IllegalArgumentException.class, () -> s.record("NOT_APPLICABLE", "EFFECTIVE", "No such system here.", who, "T"));
        s.record("NOT_APPLICABLE", null, "No such system in this entity.", who, "T");
        assertEquals("NOT_APPLICABLE", s.getStatus());
    }

    @Test
    void notStartedCannotBeSetAndBadStatusIsRefused() {
        var s = step();
        assertThrows(IllegalArgumentException.class, () -> s.record("NOT_STARTED", null, null, who, "T"));
        assertThrows(IllegalArgumentException.class, () -> s.record(null, null, null, who, "T"));
        assertThrows(IllegalArgumentException.class, () -> s.record("DONE", null, null, who, "T"));
    }

    @Test
    void reopeningClearsTheConclusion() {
        var s = step();
        s.record("COMPLETED", "INEFFECTIVE", "Leavers still active.", who, "T");
        s.record("IN_PROGRESS", "EFFECTIVE", null, who, "T");
        assertNull(s.getConclusion());
        assertEquals("IN_PROGRESS", s.getStatus());
    }

    @Test
    void ineffectiveOrPartialNeedsAFindingUntilOneIsLinked() {
        var s = step();
        s.record("COMPLETED", "PARTIALLY_EFFECTIVE", "Two leavers still active.", who, "T");
        assertTrue(s.needsFinding());
        s.linkFinding(UUID.randomUUID());
        assertFalse(s.needsFinding());
    }
}
