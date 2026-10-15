package za.co.handyflow.platform.internalaudit.application.internal;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.internalaudit.application.internal.ProgrammeSummary.StepIn;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ProgrammeSummaryTest {
    @Test
    void countsByStatusConclusionAndDomain() {
        var r = ProgrammeSummary.build(List.of(
                new StepIn("IT", "COMPLETED", "EFFECTIVE", false),
                new StepIn("IT", "COMPLETED", "INEFFECTIVE", true),
                new StepIn("IT", "NOT_APPLICABLE", null, false),
                new StepIn("CYBER", "IN_PROGRESS", null, false),
                new StepIn("CYBER", "NOT_STARTED", null, false),
                new StepIn("COMPLIANCE", "COMPLETED", "PARTIALLY_EFFECTIVE", false)));
        var o = r.overall();
        assertEquals(6, o.total());
        assertEquals(3, o.completed());
        assertEquals(1, o.notApplicable());
        assertEquals(1, o.ineffective());
        assertEquals(1, o.partial());
        assertEquals(1, o.needingFinding());
        assertEquals(67, o.donePct());
        assertEquals(3, r.byDomain().get("IT").total());
        assertEquals(100, r.byDomain().get("IT").donePct());
        assertEquals(0, r.byDomain().get("CYBER").donePct());
        assertFalse(r.byDomain().containsKey("OPERATIONAL"));
        assertEquals(List.of("IT", "CYBER", "COMPLIANCE"), List.copyOf(r.byDomain().keySet()));
    }

    @Test
    void emptyProgramme() {
        var r = ProgrammeSummary.build(List.of());
        assertEquals(0, r.overall().total());
        assertEquals(0, r.overall().donePct());
        assertTrue(r.byDomain().isEmpty());
    }
}
