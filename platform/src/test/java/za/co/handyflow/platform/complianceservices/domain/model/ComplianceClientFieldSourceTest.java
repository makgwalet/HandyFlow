package za.co.handyflow.platform.complianceservices.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ComplianceClientFieldSourceTest {

    private static final UUID ENTERER = UUID.randomUUID();

    private static ComplianceClientFieldSource src() {
        return ComplianceClientFieldSource.record(TenantId.generate(), UUID.randomUUID(), "vatNumber", "document", "Read from certificate",
                "VAT201 2026-09", LocalDate.of(2026, 9, 1), ENTERER);
    }

    @Test @DisplayName("a new record is unconfirmed")
    void unconfirmed() {
        ComplianceClientFieldSource s = src();
        assertThat(s.isVerified()).isFalse();
        assertThat(s.getSource()).isEqualTo("DOCUMENT");
    }

    @Test @DisplayName("an unknown field or source is refused")
    void unknown() {
        assertThatThrownBy(() -> ComplianceClientFieldSource.record(TenantId.generate(), UUID.randomUUID(), "shoeSize", "DOCUMENT", null, null, null, ENTERER))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ComplianceClientFieldSource.record(TenantId.generate(), UUID.randomUUID(), "vatNumber", "HEARSAY", null, null, null, ENTERER))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ComplianceClientFieldSource.record(TenantId.generate(), UUID.randomUUID(), "vatNumber", null, null, null, null, ENTERER))
                .hasMessageContaining("source is required");
    }

    @Test @DisplayName("someone else confirms it, no exception is stored")
    void otherConfirms() {
        ComplianceClientFieldSource s = src();
        UUID other = UUID.randomUUID();
        s.confirm(other, "ignored");
        assertThat(s.isVerified()).isTrue();
        assertThat(s.getVerifiedBy()).isEqualTo(other);
        assertThat(s.getVerificationExceptionReason()).isNull();
    }

    @Test @DisplayName("the person who entered it needs a recorded reason")
    void entererNeedsReason() {
        ComplianceClientFieldSource s = src();
        assertThatThrownBy(() -> s.confirm(ENTERER, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> s.confirm(ENTERER, "too short")).isInstanceOf(IllegalArgumentException.class);
        assertThat(s.isVerified()).isFalse();
        s.confirm(ENTERER, "  Sole practitioner, no second person  ");
        assertThat(s.getVerificationExceptionReason()).isEqualTo("Sole practitioner, no second person");
    }

    @Test @DisplayName("it cannot be confirmed twice")
    void twice() {
        ComplianceClientFieldSource s = src();
        s.confirm(UUID.randomUUID(), null);
        assertThatThrownBy(() -> s.confirm(UUID.randomUUID(), null)).isInstanceOf(IllegalStateException.class);
    }

    @Test @DisplayName("restating the source clears an earlier confirmation")
    void restateClears() {
        ComplianceClientFieldSource s = src();
        s.confirm(UUID.randomUUID(), null);
        s.restate("REGISTER_CHECK", "SARS eFiling lookup", null, LocalDate.of(2026, 11, 1), UUID.randomUUID());
        assertThat(s.isVerified()).isFalse();
        assertThat(s.getVerifiedAt()).isNull();
        assertThat(s.getSource()).isEqualTo("REGISTER_CHECK");
    }

    @Test @DisplayName("a confirming user is required")
    void needsUser() {
        assertThatThrownBy(() -> src().confirm(null, null)).isInstanceOf(IllegalArgumentException.class);
    }
}
