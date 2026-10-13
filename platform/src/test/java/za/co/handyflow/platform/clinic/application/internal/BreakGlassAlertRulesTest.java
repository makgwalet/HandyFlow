package za.co.handyflow.platform.clinic.application.internal;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.clinic.application.internal.BreakGlassAlertRules.Reviewer;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class BreakGlassAlertRulesTest {
    private static final UUID A = UUID.randomUUID(), B = UUID.randomUUID(), C = UUID.randomUUID();
    private static final Instant AT = Instant.parse("2026-10-13T08:00:00Z");

    @Test void theActorIsNotEmailedAboutThemselves() {
        var out = BreakGlassAlertRules.recipients(List.of(new Reviewer(A, "a@x.co", "A"), new Reviewer(B, "b@x.co", "B")), A);
        assertEquals(List.of("b@x.co"), out.stream().map(Reviewer::email).toList());
    }
    @Test void blankAddressesAndDuplicatesAreDropped() {
        var out = BreakGlassAlertRules.recipients(Arrays.asList(new Reviewer(A, " ", "A"), new Reviewer(B, "B@x.co", "B"), new Reviewer(C, "b@x.co ", "C"), null, new Reviewer(C, null, "C")), null);
        assertEquals(1, out.size());
        assertEquals("B", out.get(0).name());
    }
    @Test void noReviewersMeansNoRecipients() { assertEquals(List.of(), BreakGlassAlertRules.recipients(List.of(), A)); }

    @Test void theEmailNamesWhoAndWhenButNotThePatientOrReason() {
        String body = BreakGlassAlertRules.emailBody("Naledi", "Dr <b>Dlamini</b>", AT, AT.plusSeconds(3600));
        assertTrue(body.contains("Hello Naledi"));
        assertTrue(body.contains("Dr &lt;b&gt;Dlamini&lt;/b&gt;"));
        assertTrue(body.contains("13 Oct 2026 10:00"));   // South African time
        assertTrue(body.contains("Break-glass review"));
        assertFalse(body.toLowerCase().contains("reason:"));
    }
    @Test void anUnknownNameStillReadsWell() {
        assertTrue(BreakGlassAlertRules.emailBody(null, null, AT, AT).startsWith("<p>Hello,</p><p>A staff member opened"));
        assertEquals("Review break-glass access by A staff member", BreakGlassAlertRules.taskTitle(" "));
    }
    @Test void theTaskDetailHasNoPatientAndNoReason() {
        assertTrue(BreakGlassAlertRules.taskDetail(AT).contains("13 Oct 2026 10:00"));
        assertTrue(BreakGlassAlertRules.taskTitle("Dr Dlamini").endsWith("Dr Dlamini"));
    }
}
