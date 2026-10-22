package za.co.handyflow.platform.hr.domain.model;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static za.co.handyflow.platform.hr.domain.model.PayRunWorkflowRules.*;

class PayRunWorkflowRulesTest {

    private final UUID maker = UUID.randomUUID();
    private final UUID checker = UUID.randomUUID();

    @Test
    void onlyFinalisedAndLegacyRunsAreVisibleToEmployees() {
        assertThat(isIssued(FINALISED)).isTrue();
        assertThat(isIssued(COMPLETED)).isTrue();
        assertThat(isIssued(DRAFT)).isFalse();
        assertThat(isIssued(PREPARED)).isFalse();
        assertThat(isIssued(APPROVED)).isFalse();
        assertThat(isIssued(CANCELLED)).isFalse();
    }

    @Test
    void finalisedAndCancelledRunsAreLocked() {
        assertThat(isLocked(FINALISED)).isTrue();
        assertThat(isLocked(CANCELLED)).isTrue();
        assertThat(isLocked(APPROVED)).isFalse();
    }

    @Test
    void aRunCanBeCalculatedOnlyBeforeApproval() {
        checkCanPrepare(DRAFT);
        checkCanPrepare(PREPARED);
        for (String s : new String[] {APPROVED, FINALISED, COMPLETED, CANCELLED})
            assertThrows(IllegalStateException.class, () -> checkCanPrepare(s));
    }

    @Test
    void theMessageForALockedRunSaysSo() {
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> checkCanPrepare(FINALISED));
        assertThat(e.getMessage().contains("locked")).isTrue();
    }

    @Test
    void anotherPersonCanApprove() {
        assertThat(checkCanApprove(PREPARED, maker, checker, false, false)).isFalse();
    }

    @Test
    void thePreparerCannotApproveUnlessTheTenantAllowsIt() {
        assertThrows(AccessDeniedException.class, () -> checkCanApprove(PREPARED, maker, maker, false, false));
        assertThat(checkCanApprove(PREPARED, maker, maker, true, false)).isTrue();
    }

    @Test
    void errorsBlockApprovalEvenForAnotherPerson() {
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> checkCanApprove(PREPARED, maker, checker, false, true));
        assertThat(e.getMessage().contains("errors")).isTrue();
    }

    @Test
    void legacyCompletedRunsCanBeApprovedByAnyoneIdentified() {
        assertThat(checkCanApprove(COMPLETED, null, checker, false, false)).isFalse();
        assertThrows(AccessDeniedException.class, () -> checkCanApprove(COMPLETED, null, null, false, false));
    }

    @Test
    void onlyPreparedRunsAreApproved() {
        for (String s : new String[] {DRAFT, APPROVED, FINALISED, CANCELLED})
            assertThrows(IllegalStateException.class, () -> checkCanApprove(s, maker, checker, false, false));
    }

    @Test
    void sendingBackNeedsAReasonAndAnOpenRun() {
        checkCanReject(PREPARED, "Wrong overtime");
        checkCanReject(APPROVED, "Late change");
        assertThrows(IllegalArgumentException.class, () -> checkCanReject(PREPARED, "  "));
        assertThrows(IllegalArgumentException.class, () -> checkCanReject(PREPARED, null));
        assertThrows(IllegalStateException.class, () -> checkCanReject(FINALISED, "Too late"));
        assertThrows(IllegalStateException.class, () -> checkCanReject(COMPLETED, "Issued already"));
        assertThrows(IllegalStateException.class, () -> checkCanReject(DRAFT, "Nothing to send back"));
    }

    @Test
    void finalisingNeedsAnApprovedRunAndASecondPerson() {
        assertThat(checkCanFinalise(APPROVED, maker, checker, false)).isFalse();
        assertThrows(AccessDeniedException.class, () -> checkCanFinalise(APPROVED, maker, maker, false));
        assertThat(checkCanFinalise(APPROVED, maker, maker, true)).isTrue();
        assertThrows(IllegalStateException.class, () -> checkCanFinalise(PREPARED, maker, checker, false));
        assertThrows(IllegalStateException.class, () -> checkCanFinalise(FINALISED, maker, checker, false));
    }

    @Test
    void onlyDraftsAreCancelledAndWithAReason() {
        checkCanCancel(DRAFT, "Wrong period");
        assertThrows(IllegalStateException.class, () -> checkCanCancel(PREPARED, "Wrong period"));
        assertThrows(IllegalStateException.class, () -> checkCanCancel(APPROVED, "Wrong period"));
        assertThrows(IllegalStateException.class, () -> checkCanCancel(FINALISED, "Wrong period"));
        assertThrows(IllegalArgumentException.class, () -> checkCanCancel(DRAFT, "  "));
        assertThrows(IllegalArgumentException.class, () -> checkCanCancel(DRAFT, null));
    }
}
