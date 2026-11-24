package za.co.handyflow.platform.complianceservices.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ComplianceClientProfileTest {

    private static final UUID USER = UUID.randomUUID();

    private static ComplianceClient client() {
        return ComplianceClient.create(TenantId.generate(), "Acme", null, null, null, null, USER);
    }

    private static ComplianceClient.Profile profile(String legal, String reg, String vat, Integer emp, String band, String onboarding) {
        return new ComplianceClient.Profile(legal, "Acme Trading", reg, "pty_ltd", "9012345678", vat, "1 Main Rd", null,
                "Construction", emp, band, "low", onboarding);
    }

    @Test @DisplayName("a new client starts with onboarding not started and an empty profile")
    void startsEmpty() {
        ComplianceClient c = client();
        assertThat(c.getOnboardingStatus()).isEqualTo("NOT_STARTED");
        assertThat(c.getLegalName()).isNull();
    }

    @Test @DisplayName("first save reports every tracked fact that now has a value as changed")
    void firstSave() {
        ComplianceClient c = client();
        List<String> changed = c.updateProfile(profile("Acme (Pty) Ltd", "2015/123456/07", "4123456789", 12, "1m_to_10m", "in_progress"), USER);
        assertThat(changed).containsExactlyInAnyOrder("legalName", "registrationNumber", "entityType", "taxNumber",
                "vatNumber", "registeredAddress", "industry", "employeeCount", "turnoverBand");
        assertThat(c.getEntityType()).isEqualTo("PTY_LTD");
        assertThat(c.getTurnoverBand()).isEqualTo("1M_TO_10M");
        assertThat(c.getRiskRating()).isEqualTo("LOW");
        assertThat(c.getOnboardingStatus()).isEqualTo("IN_PROGRESS");
    }

    @Test @DisplayName("saving the same values again changes nothing")
    void unchanged() {
        ComplianceClient c = client();
        c.updateProfile(profile("Acme (Pty) Ltd", "2015/123456/07", "4123456789", 12, "1M_TO_10M", "IN_PROGRESS"), USER);
        assertThat(c.updateProfile(profile("Acme (Pty) Ltd", "2015/123456/07", "4123456789", 12, "1M_TO_10M", "IN_PROGRESS"), USER)).isEmpty();
    }

    @Test @DisplayName("only the facts that moved are reported")
    void onlyMoved() {
        ComplianceClient c = client();
        c.updateProfile(profile("Acme (Pty) Ltd", "2015/123456/07", "4123456789", 12, "1M_TO_10M", "IN_PROGRESS"), USER);
        List<String> changed = c.updateProfile(profile("Acme (Pty) Ltd", "2015/123456/07", "4123456789", 40, "1M_TO_10M", "IN_PROGRESS"), USER);
        assertThat(changed).containsExactly("employeeCount");
    }

    @Test @DisplayName("clearing a fact is a change")
    void clearing() {
        ComplianceClient c = client();
        c.updateProfile(profile("Acme (Pty) Ltd", "2015/123456/07", "4123456789", 12, "1M_TO_10M", "IN_PROGRESS"), USER);
        assertThat(c.updateProfile(profile("Acme (Pty) Ltd", "2015/123456/07", null, 12, "1M_TO_10M", "IN_PROGRESS"), USER)).containsExactly("vatNumber");
        assertThat(c.getVatNumber()).isNull();
    }

    @Test @DisplayName("a blank onboarding status keeps the current one")
    void keepsOnboarding() {
        ComplianceClient c = client();
        c.updateProfile(profile("A", "2015/123456/07", null, null, null, "complete"), USER);
        c.updateProfile(profile("A", "2015/123456/07", null, null, null, null), USER);
        assertThat(c.getOnboardingStatus()).isEqualTo("COMPLETE");
    }

    @Test @DisplayName("a bad value leaves the profile untouched")
    void badValueNoPartialChange() {
        ComplianceClient c = client();
        c.updateProfile(profile("A", "2015/123456/07", null, 5, null, null), USER);
        assertThatThrownBy(() -> c.updateProfile(profile("B", "2015/123456/07", "9999999999", 5, null, null), USER))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(c.getLegalName()).isEqualTo("A");
    }
}
