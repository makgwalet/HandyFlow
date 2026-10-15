package za.co.handyflow.platform.internalaudit;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.internalaudit.application.internal.ReportLock;
import za.co.handyflow.platform.internalaudit.domain.model.AuditReportAmendment;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ReportLockTest {

    @Test
    void noReportSubmittedMeansNothingIsLocked() {
        assertFalse(ReportLock.isLocked(null, false));
    }

    @Test
    void workIsFrozenWhileTheReportIsOutForSignOffAndOnceApproved() {
        for (String s : new String[]{"SUBMITTED", "IN_PROGRESS", "RESUBMITTED", "APPROVED"}) {
            assertTrue(ReportLock.isLocked(s, false), s);
        }
    }

    @Test
    void aRejectedReturnedOrCancelledReportCanBeWorkedOn() {
        for (String s : new String[]{"REJECTED", "RETURNED_FOR_CORRECTION", "CANCELLED"}) {
            assertFalse(ReportLock.isLocked(s, false), s);
        }
    }

    @Test
    void anOpenAmendmentUnlocksAnApprovedReport() {
        assertFalse(ReportLock.isLocked("APPROVED", true));
    }

    @Test
    void reopeningNeedsARealReasonAndIsOpenUntilResubmitted() {
        UUID t = UUID.randomUUID(), e = UUID.randomUUID(), u = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class, () -> AuditReportAmendment.open(t, e, "too short", u));
        AuditReportAmendment a = AuditReportAmendment.open(t, e, "Correct the sample size in finding 3", u);
        assertNull(a.getResubmittedAt());
        a.markResubmitted();
        assertNotNull(a.getResubmittedAt());
    }
}
