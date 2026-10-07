package za.co.handyflow.platform.clinic.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import za.co.handyflow.platform.shared.TenantId;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClinicPrescriptionFillTest {

    private static ClinicPrescription rx(int repeats) {
        TenantId tenant = Mockito.mock(TenantId.class);
        Mockito.when(tenant.getValue()).thenReturn(UUID.randomUUID());
        return ClinicPrescription.create(tenant, UUID.randomUUID(), UUID.randomUUID(), null,
                "Amoxicillin", "500mg", "TDS", "7 days", 21, repeats, null);
    }

    @Test
    @DisplayName("one original fill plus the repeats; dispensed only after the last one")
    void fillsUntilDispensed() {
        var p = rx(2);
        assertThat(p.totalFills()).isEqualTo(3);
        assertThat(p.recordFill()).isEqualTo(1);
        assertThat(p.isDispensed()).isFalse();
        assertThat(p.getDispensedAt()).isNotNull();
        assertThat(p.fillsRemaining()).isEqualTo(2);
        var first = p.getDispensedAt();
        assertThat(p.recordFill()).isEqualTo(2);
        assertThat(p.getDispensedAt()).isEqualTo(first);
        assertThat(p.recordFill()).isEqualTo(3);
        assertThat(p.isDispensed()).isTrue();
        assertThat(p.fillsRemaining()).isZero();
    }

    @Test
    @DisplayName("a fill beyond the authorised number is refused")
    void refusedWhenExhausted() {
        var p = rx(0);
        p.recordFill();
        assertThatThrownBy(p::recordFill).isInstanceOf(IllegalStateException.class).hasMessageContaining("1 authorised");
        assertThat(p.getFillsUsed()).isEqualTo(1);
    }

    @Test
    @DisplayName("markDispensed uses every remaining fill")
    void markDispensedUsesAll() {
        var p = rx(2);
        p.recordFill();
        p.markDispensed();
        assertThat(p.getFillsUsed()).isEqualTo(3);
        assertThat(p.isDispensed()).isTrue();
    }
}
