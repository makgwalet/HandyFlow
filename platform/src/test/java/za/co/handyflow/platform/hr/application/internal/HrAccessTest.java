package za.co.handyflow.platform.hr.application.internal;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class HrAccessTest {

    @Test
    void workingDetailsAreForTheOrdinaryHrPermissions() {
        assertThat(HrAccess.seesAll(Set.of("HR_READ"))).isTrue();
        assertThat(HrAccess.seesAll(Set.of("HR_MANAGE"))).isTrue();
        assertThat(HrAccess.seesAll(Set.of("PAYROLL_PREPARE"))).isTrue();
        assertThat(HrAccess.seesAll(Set.of("INVOICE_CREATE"))).isFalse();
    }

    @Test
    void readingHrDoesNotShowPersonalDataOrPay() {
        Set<String> hr = Set.of("HR_READ", "HR_MANAGE", "USER_READ", "USER_UPDATE", "PAYROLL_RUN");
        assertThat(HrAccess.sensitive(hr)).isFalse();
        assertThat(HrAccess.compensation(hr)).isFalse();
    }

    @Test
    void thePersonalAndPayPermissionsAreSeparate() {
        assertThat(HrAccess.sensitive(Set.of("HR_SENSITIVE_READ"))).isTrue();
        assertThat(HrAccess.compensation(Set.of("HR_SENSITIVE_READ"))).isFalse();
        assertThat(HrAccess.compensation(Set.of("HR_COMPENSATION_READ"))).isTrue();
        assertThat(HrAccess.sensitive(Set.of("HR_COMPENSATION_READ"))).isFalse();
    }

    @Test
    void askingForAndDecidingOnAPayChangeAreDifferentPermissions() {
        assertThat(HrAccess.canAskForPayChange(Set.of("HR_COMPENSATION_MANAGE"))).isTrue();
        assertThat(HrAccess.canDecidePayChange(Set.of("HR_COMPENSATION_MANAGE"))).isFalse();
        assertThat(HrAccess.canDecidePayChange(Set.of("HR_COMPENSATION_APPROVE"))).isTrue();
        assertThat(HrAccess.canAskForPayChange(Set.of("HR_COMPENSATION_APPROVE"))).isFalse();
    }

    @Test
    void anyoneWhoCanDecideOrAskCanAlsoSeePay() {
        assertThat(HrAccess.compensation(Set.of("HR_COMPENSATION_MANAGE"))).isTrue();
        assertThat(HrAccess.compensation(Set.of("HR_COMPENSATION_APPROVE"))).isTrue();
    }

    @Test
    void aTeamManagerSeesOnlyTheirTeamUnlessGivenMore() {
        assertThat(HrAccess.teamOnly(Set.of("HR_TEAM_READ"))).isTrue();
        assertThat(HrAccess.teamOnly(Set.of("HR_TEAM_READ", "HR_READ"))).isFalse();
        assertThat(HrAccess.teamOnly(Set.of("INVOICE_CREATE"))).isFalse();
    }
}
