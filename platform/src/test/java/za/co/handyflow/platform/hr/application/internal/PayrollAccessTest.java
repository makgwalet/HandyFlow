package za.co.handyflow.platform.hr.application.internal;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class PayrollAccessTest {

    private static final LocalDate DURING = LocalDate.of(2026, 11, 15);
    private static final LocalDate AFTER = PayrollAccess.LEGACY_ACCESS_ENDS.plusDays(1);

    @Test
    void newPermissionsWorkAtAnyTime() {
        assertThat(PayrollAccess.canRead(Set.of("PAYROLL_READ"), AFTER)).isTrue();
        assertThat(PayrollAccess.canPrepare(Set.of("PAYROLL_PREPARE"), AFTER)).isTrue();
        assertThat(PayrollAccess.canExport(Set.of("PAYROLL_STATUTORY_EXPORT"), AFTER)).isTrue();
        assertThat(PayrollAccess.canApprove(Set.of("PAYROLL_APPROVE"))).isTrue();
        assertThat(PayrollAccess.canFinalise(Set.of("PAYROLL_FINALISE"))).isTrue();
        assertThat(PayrollAccess.canConfigure(Set.of("PAYROLL_CONFIGURATION_ADMIN"))).isTrue();
    }

    @Test
    void theOldPermissionsStillPrepareUntilTheEndDate() {
        assertThat(PayrollAccess.canPrepare(Set.of("USER_UPDATE"), DURING)).isTrue();
        assertThat(PayrollAccess.canPrepare(Set.of("PAYROLL_RUN"), DURING)).isTrue();
        assertThat(PayrollAccess.canRead(Set.of("USER_UPDATE"), DURING)).isTrue();
        assertThat(PayrollAccess.canPrepare(Set.of("USER_UPDATE"), PayrollAccess.LEGACY_ACCESS_ENDS)).isTrue();
    }

    @Test
    void afterTheEndDateTheOldPermissionsStopWorking() {
        assertThat(PayrollAccess.canPrepare(Set.of("USER_UPDATE"), AFTER)).isFalse();
        assertThat(PayrollAccess.canPrepare(Set.of("PAYROLL_RUN"), AFTER)).isFalse();
        assertThat(PayrollAccess.canRead(Set.of("USER_UPDATE"), AFTER)).isFalse();
        assertThat(PayrollAccess.canExport(Set.of("PAYROLL_READ"), AFTER)).isFalse();
    }

    @Test
    void userReadAloneNeverOpenedPayrollAndStillDoesNot() {
        assertThat(PayrollAccess.canRead(Set.of("USER_READ"), DURING)).isFalse();
        assertThat(PayrollAccess.canExport(Set.of("USER_READ"), DURING)).isFalse();
    }

    @Test
    void theOldPermissionsNeverApproveOrFinalise() {
        Set<String> old = Set.of("USER_UPDATE", "PAYROLL_RUN", "USER_READ", "PAYROLL_READ", "HR_MANAGE");
        assertThat(PayrollAccess.canApprove(old)).isFalse();
        assertThat(PayrollAccess.canFinalise(old)).isFalse();
        assertThat(PayrollAccess.canConfigure(old)).isFalse();
    }

    @Test
    void preparingDoesNotImplyApproving() {
        Set<String> maker = Set.of("PAYROLL_PREPARE");
        assertThat(PayrollAccess.canApprove(maker)).isFalse();
        assertThat(PayrollAccess.canFinalise(maker)).isFalse();
        assertThat(PayrollAccess.canRead(maker, AFTER)).isTrue();
    }

    @Test
    void unrelatedPermissionsGiveNothing() {
        Set<String> other = Set.of("HR_READ", "SETTINGS_MANAGE", "INVOICE_CREATE");
        assertThat(PayrollAccess.canRead(other, DURING)).isFalse();
        assertThat(PayrollAccess.canPrepare(other, DURING)).isFalse();
        assertThat(PayrollAccess.canExport(other, DURING)).isFalse();
    }
}
