package za.co.handyflow.platform.internalaudit.domain;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.internalaudit.domain.model.AuditQualityCheck;
import za.co.handyflow.platform.internalaudit.domain.model.AuditQualityReview;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AuditQualityReviewTest {
    UUID tenant = UUID.randomUUID(), eng = UUID.randomUUID(), requester = UUID.randomUUID(), reviewer = UUID.randomUUID(), other = UUID.randomUUID();

    AuditQualityReview fresh() { return AuditQualityReview.request(tenant, eng, reviewer, "Rev", requester, "Req"); }

    @Test
    void firstAnswerStartsTheReview() {
        var r = fresh();
        assertEquals("REQUESTED", r.getStatus());
        r.answered();
        assertEquals("IN_PROGRESS", r.getStatus());
    }

    @Test
    void onlyTheAppointedReviewerMayConclude() {
        var r = fresh();
        assertThrows(IllegalStateException.class, () -> r.complete(other, "SATISFACTORY", "Everything was in order", 0, 0));
        assertThrows(IllegalStateException.class, () -> r.complete(requester, "SATISFACTORY", "Everything was in order", 0, 0));
        r.complete(reviewer, "SATISFACTORY", "Everything was in order", 0, 0);
        assertEquals("COMPLETED", r.getStatus());
        assertNotNull(r.getCompletedAt());
    }

    @Test
    void cannotConcludeWithUnansweredItemsOrABadConclusion() {
        var r = fresh();
        assertThrows(IllegalStateException.class, () -> r.complete(reviewer, "SATISFACTORY", "Everything was in order", 3, 0));
        assertThrows(IllegalArgumentException.class, () -> r.complete(reviewer, "FINE", "Everything was in order", 0, 0));
        assertThrows(IllegalArgumentException.class, () -> r.complete(reviewer, "SATISFACTORY", "short", 0, 0));
        assertEquals("REQUESTED", r.getStatus());
    }

    @Test
    void aNoAnswerBlocksASatisfactoryConclusion() {
        var r = fresh();
        assertThrows(IllegalStateException.class, () -> r.complete(reviewer, "SATISFACTORY", "Mostly fine but one gap", 0, 1));
        r.complete(reviewer, "needs_improvement", "Workpapers were not signed off", 0, 1);
        assertEquals("NEEDS_IMPROVEMENT", r.getConclusion());
    }

    @Test
    void finishedReviewsCannotBeChanged() {
        var r = fresh();
        r.complete(reviewer, "SATISFACTORY", "Everything was in order", 0, 0);
        assertThrows(IllegalStateException.class, r::answered);
        assertThrows(IllegalStateException.class, () -> r.cancel("No longer needed at all"));
        var c = fresh();
        c.cancel("Engagement was withdrawn");
        assertEquals("CANCELLED", c.getStatus());
        assertThrows(IllegalStateException.class, () -> c.requireLive());
        assertThrows(IllegalArgumentException.class, () -> fresh().cancel("short"));
    }

    @Test
    void noAndNotApplicableMustBeExplained() {
        UUID review = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class, () -> AuditQualityCheck.answer(tenant, review, "F4", "NO", null, reviewer));
        assertThrows(IllegalArgumentException.class, () -> AuditQualityCheck.answer(tenant, review, "F4", "NA", "no", reviewer));
        assertThrows(IllegalArgumentException.class, () -> AuditQualityCheck.answer(tenant, review, "F4", "MAYBE", null, reviewer));
        var yes = AuditQualityCheck.answer(tenant, review, "F4", "yes", null, reviewer);
        assertEquals("YES", yes.getResult());
        assertNull(yes.getNote());
        yes.set("NO", "Two workpapers have no sign-off", reviewer);
        assertEquals("NO", yes.getResult());
    }
}
