package za.co.handyflow.platform.earthmoving;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.earthmoving.domain.model.ReleaseRules;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ReleaseRulesTest {
    static final Instant OUT = Instant.parse("2026-10-10T08:00:00Z");
    static final UUID MECHANIC = UUID.randomUUID(), SUPERVISOR = UUID.randomUUID();

    @Test void noCompletedWorkOrderIsAProblem() {
        assertEquals(List.of(ReleaseRules.NO_WORK), ReleaseRules.problems(OUT, null, null, SUPERVISOR));
    }
    @Test void aWorkOrderFinishedBeforeTheMachineWentOutOfServiceDoesNotCount() {
        assertEquals(List.of(ReleaseRules.NO_WORK), ReleaseRules.problems(OUT, OUT.minusSeconds(60), MECHANIC, SUPERVISOR));
    }
    @Test void aWorkOrderFinishedAfterItWentOutIsEvidence() {
        assertTrue(ReleaseRules.problems(OUT, OUT.plusSeconds(3600), MECHANIC, SUPERVISOR).isEmpty());
        assertTrue(ReleaseRules.problems(OUT, OUT, MECHANIC, SUPERVISOR).isEmpty());
    }
    @Test void theSamePersonReleasingTheirOwnWorkIsAProblem() {
        assertEquals(List.of(ReleaseRules.SAME_PERSON), ReleaseRules.problems(OUT, OUT.plusSeconds(1), MECHANIC, MECHANIC));
    }
    @Test void unknownTimeOutOfServiceAcceptsAnyCompletedWorkOrder() {
        assertTrue(ReleaseRules.problems(null, Instant.EPOCH, MECHANIC, SUPERVISOR).isEmpty());
        assertEquals(List.of(ReleaseRules.NO_WORK), ReleaseRules.problems(null, null, null, SUPERVISOR));
    }
    @Test void aMissingCompletedByNeverCountsAsTheSamePerson() {
        assertTrue(ReleaseRules.problems(OUT, OUT.plusSeconds(1), null, SUPERVISOR).isEmpty());
    }
    @Test void noEvidenceReportsOnlyThatProblem() {
        assertEquals(1, ReleaseRules.problems(OUT, null, MECHANIC, MECHANIC).size());
    }
}
