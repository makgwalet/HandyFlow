package za.co.handyflow.platform.clinic.application.internal;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class VisitTeamRulesTest {

    static final UUID NURSE = UUID.randomUUID(), DOCTOR = UUID.randomUUID(), CLERK = UUID.randomUUID();
    static final Map<UUID, String> NAMES = Map.of(NURSE, "Sister Zodwa Nkosi", DOCTOR, "Dr Andile Dlamini", CLERK, "Palesa Mokoena");
    static final Instant T = Instant.parse("2026-10-08T08:00:00Z");

    private List<VisitTeamRules.Member> team(List<VisitTeamRules.Step> steps, UUID started, UUID signed, String practitioner) {
        return VisitTeamRules.team(steps, started, signed, T, practitioner, NAMES::get);
    }

    @Test
    void aHandoffVisitNamesWhoPreparedWhoReviewedAndWhoSigned() {
        var steps = List.of(
                new VisitTeamRules.Step("NURSE_IN_PROGRESS", NURSE, T),
                new VisitTeamRules.Step("READY_FOR_DOCTOR", NURSE, T.plusSeconds(600)),
                new VisitTeamRules.Step("DOCTOR_REVIEWING", DOCTOR, T.plusSeconds(700)),
                new VisitTeamRules.Step("DOCTOR_COMPLETED", DOCTOR, T.plusSeconds(900)));
        var t = team(steps, CLERK, DOCTOR, null);
        assertEquals(List.of("Prepared by|Sister Zodwa Nkosi", "Reviewed by|Dr Andile Dlamini", "Signed by|Dr Andile Dlamini"),
                t.stream().map(m -> m.role() + "|" + m.name()).toList());
    }

    @Test
    void aVisitWithoutHandoffNamesWhoStartedItAndWhoSigned() {
        var t = team(List.of(), DOCTOR, DOCTOR, "Dr Andile Dlamini");
        assertEquals(List.of("Started by|Dr Andile Dlamini", "Signed by|Dr Andile Dlamini"), t.stream().map(m -> m.role() + "|" + m.name()).toList().subList(0, 2));
        assertEquals(3, t.size());   // plus "Seen by" the booked practitioner
        assertEquals("Seen by", t.get(2).role());
    }

    @Test
    void theSameNameIsNotRepeatedInTheSameRole() {
        var steps = List.of(new VisitTeamRules.Step("NURSE_IN_PROGRESS", NURSE, T), new VisitTeamRules.Step("READY_FOR_DOCTOR", NURSE, T));
        assertEquals(1L, team(steps, null, null, null).stream().filter(m -> m.role().equals("Prepared by")).count());
    }

    @Test
    void peopleWithNoNameOnRecordAreLeftOutRatherThanInvented() {
        var steps = List.of(new VisitTeamRules.Step("READY_FOR_DOCTOR", UUID.randomUUID(), T));
        assertTrue(team(steps, null, UUID.randomUUID(), "  ").isEmpty());
    }
}
