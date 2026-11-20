package za.co.handyflow.platform.legalcompliance.application.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.legalcompliance.application.internal.WrittenResolutionRules.Counts;
import za.co.handyflow.platform.legalcompliance.domain.model.*;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class WrittenResolutionRulesTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 19);
    private static final TenantId T = TenantId.of(UUID.randomUUID());

    private static Counts c(int f, int a, int ab, int n) { return new Counts(f, a, ab, n); }

    @Test @DisplayName("Counting treats a missing answer as no response")
    void counting() {
        Counts k = WrittenResolutionRules.count(Arrays.asList(WrittenChoice.FOR, WrittenChoice.FOR, WrittenChoice.AGAINST, WrittenChoice.ABSTAIN, null));
        assertEquals(c(2, 1, 1, 1), k);
        assertEquals(5, k.total());
        assertEquals(4, k.answered());
    }

    @Test @DisplayName("Unanimous: only when every member is for")
    void unanimous() {
        assertEquals(WrittenStatus.CARRIED, WrittenResolutionRules.outcome(ApprovalThreshold.UNANIMOUS, c(4, 0, 0, 0)));
        assertEquals(WrittenStatus.NOT_CARRIED, WrittenResolutionRules.outcome(ApprovalThreshold.UNANIMOUS, c(3, 1, 0, 0)));
        assertEquals(WrittenStatus.NOT_CARRIED, WrittenResolutionRules.outcome(ApprovalThreshold.UNANIMOUS, c(3, 0, 1, 0)), "an abstention stops it");
        assertEquals(WrittenStatus.NOT_CARRIED, WrittenResolutionRules.outcome(ApprovalThreshold.UNANIMOUS, c(3, 0, 0, 1)), "so does silence");
    }

    @Test @DisplayName("Majority of all: more than half of ALL members, so silence, abstentions and ties count against")
    void majorityOfAll() {
        assertEquals(WrittenStatus.CARRIED, WrittenResolutionRules.outcome(ApprovalThreshold.MAJORITY_OF_ALL, c(2, 1, 0, 0)));
        assertEquals(WrittenStatus.CARRIED, WrittenResolutionRules.outcome(ApprovalThreshold.MAJORITY_OF_ALL, c(2, 0, 0, 1)), "2 of 3 even though one stayed silent");
        assertEquals(WrittenStatus.NOT_CARRIED, WrittenResolutionRules.outcome(ApprovalThreshold.MAJORITY_OF_ALL, c(2, 0, 1, 1)), "2 of 4 is a tie");
        assertEquals(WrittenStatus.NOT_CARRIED, WrittenResolutionRules.outcome(ApprovalThreshold.MAJORITY_OF_ALL, c(2, 0, 0, 2)));
        assertEquals(WrittenStatus.CARRIED, WrittenResolutionRules.outcome(ApprovalThreshold.MAJORITY_OF_ALL, c(3, 0, 1, 0)));
        assertEquals(WrittenStatus.CARRIED, WrittenResolutionRules.outcome(ApprovalThreshold.MAJORITY_OF_ALL, c(1, 0, 0, 0)));
    }

    @Test @DisplayName("Nobody to count means not carried")
    void empty() {
        assertEquals(WrittenStatus.NOT_CARRIED, WrittenResolutionRules.outcome(ApprovalThreshold.MAJORITY_OF_ALL, c(0, 0, 0, 0)));
        assertEquals(WrittenStatus.NOT_CARRIED, WrittenResolutionRules.outcome(ApprovalThreshold.UNANIMOUS, c(0, 0, 0, 0)));
    }

    @Test @DisplayName("It can be closed once everyone has answered, or after the deadline day, not before")
    void closeProblem() {
        LocalDate deadline = TODAY.plusDays(5);
        assertNull(WrittenResolutionRules.closeProblem(c(3, 0, 0, 0), deadline, TODAY));
        assertNotNull(WrittenResolutionRules.closeProblem(c(2, 0, 0, 1), deadline, TODAY));
        assertNotNull(WrittenResolutionRules.closeProblem(c(2, 0, 0, 1), deadline, deadline), "answers are still welcome on the deadline day");
        assertNull(WrittenResolutionRules.closeProblem(c(2, 0, 0, 1), deadline, deadline.plusDays(1)));
        assertTrue(WrittenResolutionRules.closeProblem(c(2, 0, 0, 1), deadline, TODAY).contains("1 member(s)"));
    }

    @Test @DisplayName("Reminders go out 2 days before and on the deadline day only")
    void reminders() {
        LocalDate d = TODAY.plusDays(10);
        assertTrue(WrittenResolutionRules.remindToday(d, d.minusDays(2)));
        assertTrue(WrittenResolutionRules.remindToday(d, d));
        for (int n : new int[]{9, 5, 3, 1}) assertFalse(WrittenResolutionRules.remindToday(d, d.minusDays(n)), n + " days before");
        assertFalse(WrittenResolutionRules.remindToday(d, d.plusDays(1)));
        assertEquals("closes in 2 days (" + d + ")", WrittenResolutionRules.reminderText(d, d.minusDays(2)));
        assertEquals("closes today", WrittenResolutionRules.reminderText(d, d));
    }

    private WrittenResolution make(int members, int quorum, LocalDate deadline) {
        return WrittenResolution.circulate(T, UUID.randomUUID(), "WRS-00001", "Approve the policy", "Approve the revised anti-bribery policy as circulated", ApprovalThreshold.UNANIMOUS, deadline, members, quorum, TODAY, "Sec");
    }

    @Test @DisplayName("Circulating checks the title, text, deadline and that enough members exist for the quorum")
    void circulateValidation() {
        assertNotNull(make(3, 2, TODAY.plusDays(7)));
        assertThrows(IllegalArgumentException.class, () -> WrittenResolution.circulate(T, UUID.randomUUID(), "R", " ", "Approve the revised anti-bribery policy", ApprovalThreshold.UNANIMOUS, TODAY.plusDays(3), 3, 2, TODAY, "S"));
        assertThrows(IllegalArgumentException.class, () -> WrittenResolution.circulate(T, UUID.randomUUID(), "R", "T", "too short", ApprovalThreshold.UNANIMOUS, TODAY.plusDays(3), 3, 2, TODAY, "S"));
        assertThrows(IllegalArgumentException.class, () -> WrittenResolution.circulate(T, UUID.randomUUID(), "R", "T", "Approve the revised anti-bribery policy", null, TODAY.plusDays(3), 3, 2, TODAY, "S"));
        assertThrows(IllegalArgumentException.class, () -> make(3, 2, TODAY), "the deadline must be after today");
        assertThrows(IllegalArgumentException.class, () -> make(3, 2, TODAY.minusDays(1)));
        assertThrows(IllegalArgumentException.class, () -> make(3, 2, TODAY.plusDays(61)));
        assertNotNull(make(3, 2, TODAY.plusDays(60)));
        assertThrows(IllegalArgumentException.class, () -> make(1, 2, TODAY.plusDays(7)), "fewer voting members than the quorum");
    }

    @Test @DisplayName("Closing fixes the counts, which must add up to the members it was sent to; it cannot be closed or cancelled twice")
    void closing() {
        WrittenResolution w = make(3, 2, TODAY.plusDays(7));
        assertThrows(IllegalStateException.class, () -> w.close(WrittenStatus.CARRIED, c(2, 0, 0, 0), TODAY, false), "counts must add up");
        assertThrows(IllegalArgumentException.class, () -> w.close(WrittenStatus.OPEN, c(3, 0, 0, 0), TODAY, false));
        w.close(WrittenStatus.CARRIED, c(3, 0, 0, 0), TODAY, true);
        assertEquals(WrittenStatus.CARRIED, w.getStatus());
        assertTrue(w.isClosedAutomatically());
        assertThrows(IllegalStateException.class, () -> w.close(WrittenStatus.NOT_CARRIED, c(0, 3, 0, 0), TODAY, false));
        assertThrows(IllegalStateException.class, () -> w.cancel("Withdrawn because it was sent in error", TODAY));
    }

    @Test @DisplayName("Cancelling needs a reason")
    void cancelling() {
        WrittenResolution w = make(3, 2, TODAY.plusDays(7));
        assertThrows(IllegalArgumentException.class, () -> w.cancel("too short", TODAY));
        assertTrue(w.isOpen());
        w.cancel("Sent to the wrong committee by mistake", TODAY);
        assertEquals(WrittenStatus.CANCELLED, w.getStatus());
    }

    @Test @DisplayName("A member with an account answers for themselves; for one without, the secretary records the answer and how it came")
    void onBehalf() {
        WrittenResponse withAccount = WrittenResponse.assign(T, UUID.randomUUID(), UUID.randomUUID(), "Anna", UUID.randomUUID());
        assertThrows(IllegalStateException.class, () -> withAccount.recordOnBehalf(WrittenChoice.FOR, null, "Email of 18 Nov 2026", "Sec"));
        WrittenResponse none = WrittenResponse.assign(T, UUID.randomUUID(), UUID.randomUUID(), "Cara", null);
        assertTrue(none.isPending());
        assertThrows(IllegalArgumentException.class, () -> none.recordOnBehalf(WrittenChoice.FOR, null, "short", "Sec"));
        assertTrue(none.isPending());
        none.recordOnBehalf(WrittenChoice.AGAINST, "  Concerned about cost ", "Email from Cara, 18 Nov 2026", "Sec");
        assertEquals(WrittenChoice.AGAINST, none.getChoice());
        assertEquals("Concerned about cost", none.getReason());
        assertEquals("Sec", none.getRecordedByName());
        withAccount.respond(WrittenChoice.FOR, "");
        assertNull(withAccount.getReason());
        assertNull(withAccount.getRecordedByName());
        assertNotNull(withAccount.getRespondedAt());
        assertThrows(IllegalArgumentException.class, () -> withAccount.respond(null, null));
    }
}
