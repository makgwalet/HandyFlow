package za.co.handyflow.platform.complianceservices.domain.model;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProviderRulesTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 25);
    private static final TenantId TENANT = TenantId.of(UUID.fromString("00000000-0000-0000-0000-0000000000a1"));
    private static final UUID PROVIDER = UUID.fromString("00000000-0000-0000-0000-0000000000d1");
    private static final UUID ME = UUID.fromString("00000000-0000-0000-0000-0000000000b1");
    private static final UUID OTHER = UUID.fromString("00000000-0000-0000-0000-0000000000b2");

    private ComplianceProviderAccreditation claim(LocalDate expires) {
        return ComplianceProviderAccreditation.claim(TENANT, PROVIDER, " SANAS ", " B-123 ", TODAY.minusYears(1), expires, ME);
    }

    @Test
    void anEnteredAccreditationIsOnlyAClaim() {
        ComplianceProviderAccreditation a = claim(TODAY.plusYears(1));
        assertThat(a.getStatus().equals("CLAIMED")).isTrue();
        assertThat(a.isUsable(TODAY)).isFalse();
        assertThat(a.getBody().equals("SANAS")).isTrue();
        assertThat(a.getReference().equals("B-123")).isTrue();
    }

    @Test
    void theSamePersonCannotCheckTheirOwnEntry() {
        ComplianceProviderAccreditation a = claim(null);
        assertThatThrownBy(() -> a.check(true, ME, "Looked it up on the register")).isInstanceOf(IllegalStateException.class);
        assertThat(a.getStatus().equals("CLAIMED")).isTrue();
    }

    @Test
    void checkingNeedsAHowAndHappensOnce() {
        ComplianceProviderAccreditation a = claim(null);
        assertThatThrownBy(() -> a.check(true, OTHER, "ok")).isInstanceOf(IllegalArgumentException.class);
        a.check(true, OTHER, "Matched on the body's online register");
        assertThat(a.isUsable(TODAY)).isTrue();
        assertThatThrownBy(() -> a.check(false, OTHER, "Changed my mind on this one")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectedOrExpiredOrRetiredIsNeverUsable() {
        ComplianceProviderAccreditation rejected = claim(null);
        rejected.check(false, OTHER, "Number not found on the register");
        assertThat(rejected.isUsable(TODAY)).isFalse();
        ComplianceProviderAccreditation expired = claim(TODAY.minusDays(1));
        expired.check(true, OTHER, "Matched on the body's online register");
        assertThat(expired.isUsable(TODAY)).isFalse();
        ComplianceProviderAccreditation retired = claim(null);
        retired.check(true, OTHER, "Matched on the body's online register");
        retired.retire(ME, "Replaced by a newer certificate");
        assertThat(retired.isUsable(TODAY)).isFalse();
        assertThatThrownBy(() -> retired.retire(ME, "Replaced by a newer certificate")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void expiryStates() {
        assertThat(ProviderRules.expiry(null, TODAY).equals("NO_EXPIRY")).isTrue();
        assertThat(ProviderRules.expiry(TODAY.minusDays(1), TODAY).equals("EXPIRED")).isTrue();
        assertThat(ProviderRules.expiry(TODAY, TODAY).equals("EXPIRING")).isTrue();
        assertThat(ProviderRules.expiry(TODAY.plusDays(30), TODAY).equals("EXPIRING")).isTrue();
        assertThat(ProviderRules.expiry(TODAY.plusDays(31), TODAY).equals("VALID")).isTrue();
    }

    @Test
    void datesMustMakeSense() {
        assertThatThrownBy(() -> ComplianceProviderAccreditation.claim(TENANT, PROVIDER, "SANAS", "B-1", TODAY, TODAY.minusDays(1), ME)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ComplianceProviderAccreditation.claim(TENANT, PROVIDER, " ", "B-1", null, null, ME)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void queueSeverities() {
        assertThat(ProviderRules.queueItem("Pat", "SANAS", "CLAIMED", null, TODAY.minusDays(2), TODAY).severity().equals(WorkQueueRules.LOW)).isTrue();
        assertThat(ProviderRules.queueItem("Pat", "SANAS", "CLAIMED", null, TODAY.minusDays(8), TODAY).severity().equals(WorkQueueRules.MEDIUM)).isTrue();
        assertThat(ProviderRules.queueItem("Pat", "SANAS", "VERIFIED", TODAY.minusDays(1), TODAY.minusYears(1), TODAY).severity().equals(WorkQueueRules.HIGH)).isTrue();
        assertThat(ProviderRules.queueItem("Pat", "SANAS", "VERIFIED", TODAY.plusDays(10), TODAY.minusYears(1), TODAY).severity().equals(WorkQueueRules.MEDIUM)).isTrue();
        assertThat(ProviderRules.queueItem("Pat", "SANAS", "VERIFIED", TODAY.plusDays(90), TODAY.minusYears(1), TODAY) == null).isTrue();
        assertThat(ProviderRules.queueItem("Pat", "SANAS", "VERIFIED", null, TODAY.minusYears(1), TODAY) == null).isTrue();
        assertThat(ProviderRules.queueItem("Pat", "SANAS", "VERIFIED", TODAY.plusDays(10), TODAY.minusYears(1), TODAY).tab().equals("providers")).isTrue();
    }

    @Test
    void providerProfileIsTidiedAndChecked() {
        ComplianceProvider p = ComplianceProvider.create(TENANT, " Pat Verifier ", "firm", " PAT@Example.com ", null, "Gauteng", null, ME);
        assertThat(p.getName().equals("Pat Verifier")).isTrue();
        assertThat(p.getKind().equals("FIRM")).isTrue();
        assertThat(p.getEmail().equals("pat@example.com")).isTrue();
        assertThatThrownBy(() -> ComplianceProvider.create(TENANT, "X", null, "not-an-email", null, null, null, ME)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ComplianceProvider.create(TENANT, "X", "robot", null, null, null, null, ME)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void inactiveNeedsAReasonAndCanComeBack() {
        ComplianceProvider p = ComplianceProvider.create(TENANT, "Pat", null, null, null, null, null, ME);
        assertThatThrownBy(() -> p.deactivate("no")).isInstanceOf(IllegalArgumentException.class);
        p.deactivate("Stopped working with us in 2026");
        assertThat(p.isActive()).isFalse();
        assertThatThrownBy(() -> p.deactivate("Stopped working with us in 2026")).isInstanceOf(IllegalStateException.class);
        p.reactivate();
        assertThat(p.isActive()).isTrue();
        assertThat(p.getInactiveReason() == null).isTrue();
    }

    @Test
    void serviceCodesAreTidiedWithoutRepeats() {
        assertThat(ProviderRules.serviceCodes(Arrays.asList(" CS-A ", "CS-A", "", null, "CS-B")).size() == 2).isTrue();
        assertThat(ProviderRules.serviceCodes(null).isEmpty()).isTrue();
    }
}
