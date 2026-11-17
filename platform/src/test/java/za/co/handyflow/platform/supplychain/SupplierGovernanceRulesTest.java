package za.co.handyflow.platform.supplychain;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.supplychain.application.internal.SupplierBankRules;
import za.co.handyflow.platform.supplychain.domain.enums.SupplierStatus;
import za.co.handyflow.platform.supplychain.domain.model.ScSupplier;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SupplierGovernanceRulesTest {

    private ScSupplier supplier() {
        return ScSupplier.create(UUID.randomUUID(), "Acme", null, null, null, null, null, "a@acme.co.za", null,
                null, null, null, null, null, null, "FNB", "62123456789", "250655", 30, "ZAR", null, UUID.randomUUID());
    }

    @Test
    void newSupplierIsActiveAndOrderable_pendingIsNot() {
        ScSupplier s = supplier();
        assertThat(s.isOrderable()).isTrue();
        s.requireApproval();
        assertThat(s.getStatus()).isEqualTo(SupplierStatus.PENDING_APPROVAL);
        assertThat(s.isOrderable()).isFalse();
    }

    @Test
    void legalAndIllegalTransitions() {
        ScSupplier s = supplier();
        UUID by = UUID.randomUUID();
        s.transitionTo(SupplierStatus.SUSPENDED, "Tax clearance expired", by);
        assertThat(s.isOrderable()).isFalse();
        assertThat(s.getStatusReason()).isEqualTo("Tax clearance expired");
        assertThat(s.getStatusChangedBy()).isEqualTo(by);
        s.transitionTo(SupplierStatus.BLACKLISTED, "Fraud", by);
        s.transitionTo(SupplierStatus.ACTIVE, "Cleared", by);
        assertThat(s.isOrderable()).isTrue();

        ScSupplier pending = supplier();
        pending.requireApproval();
        assertThatThrownBy(() -> pending.transitionTo(SupplierStatus.SUSPENDED, "x", by))
                .isInstanceOf(IllegalStateException.class);
        assertThat(SupplierStatus.INACTIVE.canTransitionTo(SupplierStatus.BLACKLISTED)).isFalse();
    }

    @Test
    void accountIsMasked_andOnlyChangesThroughApplyBankDetails() {
        ScSupplier s = supplier();
        assertThat(s.getBankAccountMasked()).isEqualTo("****6789");
        s.update("Acme 2", null, null, null, null, null, null, null);
        assertThat(s.getBankAccountMasked()).isEqualTo("****6789");   // profile edit can't touch banking
        s.applyBankDetails("Nedbank", "1234567890", "198765");
        assertThat(s.getBankAccountMasked()).isEqualTo("****7890");
        assertThat(s.getBankName()).isEqualTo("Nedbank");
    }

    @Test
    void bankRules_normaliseAndValidate() {
        assertThat(SupplierBankRules.validateRequired("6212 3456-789", "25 06 55").account()).isEqualTo("62123456789");
        assertThat(SupplierBankRules.validateRequired("62123456789", "250655").branch()).isEqualTo("250655");
        assertThat(SupplierBankRules.validateOptional(null, "").account()).isNull();
        assertThatThrownBy(() -> SupplierBankRules.validateRequired("ABC123", null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SupplierBankRules.validateRequired("1234", null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SupplierBankRules.validateRequired("62123456789", "123")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SupplierBankRules.validateRequired(" ", null)).isInstanceOf(IllegalArgumentException.class);
        assertThat(SupplierBankRules.mask("62123456789")).isEqualTo("****6789");
        assertThat(SupplierBankRules.mask("123")).isEqualTo("****");
    }
}
