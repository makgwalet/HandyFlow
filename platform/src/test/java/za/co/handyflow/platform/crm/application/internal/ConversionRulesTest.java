package za.co.handyflow.platform.crm.application.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.crm.domain.model.CustomerType;

class ConversionRulesTest {
    @Test
    void aCustomerWithNothingOpenCanBeTurnedBackIntoALead() {
        ConversionRules.checkUndo(CustomerType.CUSTOMER, 0, 0);
    }

    @Test
    void aLeadHasNothingToUndo() {
        assertThatThrownBy(() -> ConversionRules.checkUndo(CustomerType.LEAD, 0, 0)).isInstanceOf(IllegalStateException.class).hasMessage("This record is already a lead");
    }

    @Test
    void openBookingsAndUnpaidInvoicesBlockItAndAreCounted() {
        assertThatThrownBy(() -> ConversionRules.checkUndo(CustomerType.CUSTOMER, 1, 0)).hasMessage("1 active booking exists. Cancel or complete it first.");
        assertThatThrownBy(() -> ConversionRules.checkUndo(CustomerType.CUSTOMER, 3, 2)).hasMessage("3 active bookings exist. Cancel or complete them first.");
        assertThatThrownBy(() -> ConversionRules.checkUndo(CustomerType.CUSTOMER, 0, 1)).hasMessage("1 unpaid invoice exists. Settle or void it first.");
        assertThatThrownBy(() -> ConversionRules.checkUndo(CustomerType.CUSTOMER, 0, 4)).hasMessage("4 unpaid invoices exist. Settle or void them first.");
    }

    @Test
    void theReasonIsOptionalTrimmedAndLimited() {
        assertThat(ConversionRules.cleanReason(null)).isNull();
        assertThat(ConversionRules.cleanReason("   ")).isNull();
        assertThat(ConversionRules.cleanReason("  Converted by mistake ")).isEqualTo("Converted by mistake");
        assertThat(ConversionRules.cleanReason("x".repeat(500))).hasSize(500);
        assertThatThrownBy(() -> ConversionRules.cleanReason("x".repeat(501))).isInstanceOf(IllegalArgumentException.class).hasMessage("The reason can be at most 500 characters");
    }
}
