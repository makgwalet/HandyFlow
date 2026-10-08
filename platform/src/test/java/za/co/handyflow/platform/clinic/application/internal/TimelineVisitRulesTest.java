package za.co.handyflow.platform.clinic.application.internal;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.clinic.dto.VisitDtos.*;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TimelineVisitRulesTest {

    private static VisitResponse visit(String complaint, String diagnosis, List<String> codes, String plan, Integer followUp,
                                       List<TeamMember> team, int meds, int addenda) {
        List<VisitPrescription> rx = new java.util.ArrayList<>();
        for (int i = 0; i < meds; i++) rx.add(new VisitPrescription(UUID.randomUUID(), "Med" + i, null, null, null, 1, 0, null, null, false));
        List<VisitAddendum> ad = new java.util.ArrayList<>();
        for (int i = 0; i < addenda; i++) ad.add(new VisitAddendum(UUID.randomUUID(), "x", "Dr A", null));
        return new VisitResponse(UUID.randomUUID(), null, "SIGNED", null, null, null, team, complaint, null, null, diagnosis, codes, plan, followUp,
                null, null, null, null, null, null, false, null, rx, ad);
    }

    @Test
    void summaryListsReasonDiagnosisPlanMedicinesFollowUpAndAddenda() {
        var v = visit(" Cough ", "Acute bronchitis", List.of("J20.9"), "Rest and fluids", 7, List.of(), 2, 1);
        assertEquals(List.of("Reason: Cough", "Diagnosis: Acute bronchitis (J20.9)", "Plan: Rest and fluids",
                "2 medicines prescribed", "Follow-up in 7 days", "1 addendum after signing"), TimelineVisitRules.summary(v));
    }

    @Test
    void summarySkipsWhatWasNotRecordedAndUsesSingularWording() {
        var v = visit(null, " ", null, null, 1, List.of(), 1, 0);
        assertEquals(List.of("1 medicine prescribed", "Follow-up in 1 day"), TimelineVisitRules.summary(v));
        assertTrue(TimelineVisitRules.summary(visit(null, null, null, null, null, List.of(), 0, 0)).isEmpty());
    }

    @Test
    void peopleKeepTheRecordedOrderAndSkipNamelessMembers() {
        var v = visit("x", null, null, null, null, java.util.Arrays.asList(
                new TeamMember("Prepared by", "Sister Zodwa Nkosi", null), new TeamMember("Reviewed by", " ", null),
                null, new TeamMember("Signed by", "Dr Andile Dlamini", null)), 0, 0);
        assertEquals(List.of("Prepared by Sister Zodwa Nkosi", "Signed by Dr Andile Dlamini"), TimelineVisitRules.people(v));
    }
}
