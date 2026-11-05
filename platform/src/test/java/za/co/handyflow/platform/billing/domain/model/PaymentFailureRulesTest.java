package za.co.handyflow.platform.billing.domain.model;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static za.co.handyflow.platform.billing.domain.model.PaymentFailureRules.*;

class PaymentFailureRulesTest {

    private static final Instant FAILED = Instant.parse("2026-11-01T08:00:00Z");

    private static Access at(long days) { return stage(FAILED, FAILED.plus(Duration.ofDays(days))).access(); }

    @Test
    void noPaymentFailureMeansFullAccess() {
        assertThat(stage(null, Instant.now()).access()).isEqualTo(Access.FULL);
    }

    @Test
    void thereIsFullAccessForTheSevenDayGrace() {
        assertThat(at(0)).isEqualTo(Access.FULL);
        assertThat(at(6)).isEqualTo(Access.FULL);
        assertThat(at(7)).isEqualTo(Access.FULL);   // the seventh day is still grace
    }

    @Test
    void afterGraceWritesStopButReadingContinuesForThirtyDays() {
        assertThat(stage(FAILED, FAILED.plus(Duration.ofDays(7)).plusSeconds(1)).access()).isEqualTo(Access.READ_ONLY);
        assertThat(at(20)).isEqualTo(Access.READ_ONLY);
        assertThat(at(37)).isEqualTo(Access.READ_ONLY);   // the last recovery day
    }

    @Test
    void afterRecoveryOperationalAccessIsRestricted() {
        assertThat(stage(FAILED, FAILED.plus(Duration.ofDays(37)).plusSeconds(1)).access()).isEqualTo(Access.RESTRICTED);
        assertThat(at(200)).isEqualTo(Access.RESTRICTED);
    }

    @Test
    void theStageBoundariesAreReportedForTheScreenAndTheLog() {
        var s = stage(FAILED, FAILED.plus(Duration.ofDays(2)));
        assertThat(s.graceEndsAt()).isEqualTo(FAILED.plus(Duration.ofDays(7)));
        assertThat(s.recoveryEndsAt()).isEqualTo(FAILED.plus(Duration.ofDays(37)));
        assertThat(s.daysLeftInStage()).isEqualTo(5);
    }

    @Test
    void whatEachStageAllowsInPaidModules() {
        assertThat(allowsPaidModuleWrite(Access.FULL)).isTrue();
        assertThat(allowsPaidModuleWrite(Access.READ_ONLY)).isFalse();
        assertThat(allowsPaidModuleWrite(Access.RESTRICTED)).isFalse();
        assertThat(allowsPaidModuleRead(Access.FULL)).isTrue();
        assertThat(allowsPaidModuleRead(Access.READ_ONLY)).isTrue();
        assertThat(allowsPaidModuleRead(Access.RESTRICTED)).isFalse();
    }

    @Test
    void signInSecurityBillingAndExportAreAlwaysAvailable() {
        for (String area : new String[] {"AUTHENTICATION", "SECURITY", "BILLING", "DATA_EXPORT"}) assertThat(alwaysAvailable(area)).as(area).isTrue();
        assertThat(alwaysAvailable("INVOICING")).isFalse();
        assertThat(alwaysAvailable(null)).isFalse();
    }

    @Test
    void aSuccessfulPaymentClearsTheFailureAndRestoresAccess() {
        // Billing clears pastDueSince on reinstatement (Subscription.reinstate), and no failure date means full access.
        assertThat(stage(null, FAILED.plus(Duration.ofDays(100))).access()).isEqualTo(Access.FULL);
    }
}
