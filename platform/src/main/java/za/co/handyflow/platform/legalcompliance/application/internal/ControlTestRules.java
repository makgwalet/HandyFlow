package za.co.handyflow.platform.legalcompliance.application.internal;

import za.co.handyflow.platform.legalcompliance.domain.model.ControlFrequency;
import za.co.handyflow.platform.legalcompliance.domain.model.FindingSeverity;
import za.co.handyflow.platform.legalcompliance.domain.model.OperatingEffectiveness;
import za.co.handyflow.platform.legalcompliance.domain.model.TestResult;

import java.time.LocalDate;

/**
 * Pure rules linking test outcomes to control effectiveness, retest timing and finding severity.
 * The intervals are defaults (a control should be re-tested at least this often to stay "currently effective"); they are
 * a starting point for an organisation's own assurance plan, not a regulatory requirement.
 */
public final class ControlTestRules {
    private ControlTestRules() {}

    public static final int FIRST_TEST_GRACE_DAYS = 30;
    public static final int RETEST_AFTER_FAILURE_DAYS = 30;

    public static OperatingEffectiveness effectivenessFor(TestResult result) {
        return switch (result) {
            case PASS -> OperatingEffectiveness.EFFECTIVE;
            case PARTIAL -> OperatingEffectiveness.PARTIALLY_EFFECTIVE;
            case FAIL -> OperatingEffectiveness.INEFFECTIVE;
        };
    }

    public static int intervalDays(ControlFrequency f) {
        return switch (f) {
            case CONTINUOUS, DAILY, WEEKLY, MONTHLY -> 90;
            case QUARTERLY -> 180;
            case ANNUALLY, AD_HOC -> 365;
        };
    }

    /** A pass is good for the control's interval; a partial or failed result is re-tested soon. */
    public static LocalDate nextDue(LocalDate completedOn, ControlFrequency frequency, TestResult result) {
        return result == TestResult.PASS ? completedOn.plusDays(intervalDays(frequency)) : completedOn.plusDays(RETEST_AFTER_FAILURE_DAYS);
    }

    /** "Effective" only counts while the test is still current. A lapsed pass is reported as stale, not effective. */
    public static boolean currentlyEffective(OperatingEffectiveness e, LocalDate nextTestDue, LocalDate today) {
        return e == OperatingEffectiveness.EFFECTIVE && nextTestDue != null && !nextTestDue.isBefore(today);
    }

    public static FindingSeverity severityFor(TestResult result) {
        return switch (result) { case FAIL -> FindingSeverity.HIGH; case PARTIAL -> FindingSeverity.MEDIUM; case PASS -> throw new IllegalArgumentException("A passing test raises no finding"); };
    }

    public static boolean raisesFinding(TestResult result) { return result != TestResult.PASS; }

    public static int remediationDays(FindingSeverity s) {
        return switch (s) { case CRITICAL -> 14; case HIGH -> 30; case MEDIUM -> 60; case LOW -> 90; };
    }
}
