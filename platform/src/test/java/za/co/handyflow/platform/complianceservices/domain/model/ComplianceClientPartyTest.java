package za.co.handyflow.platform.complianceservices.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ComplianceClientPartyTest {

    private static final UUID USER = UUID.randomUUID();

    private static ComplianceClientParty.Details d(String name, String role, String pct, LocalDate from, LocalDate to) {
        return new ComplianceClientParty.Details(name, null, "sa_id", "8001015009087", role, pct == null ? null : new BigDecimal(pct), from, to, null);
    }

    private static ComplianceClientParty party(String role, String pct) {
        return ComplianceClientParty.create(TenantId.generate(), UUID.randomUUID(), d("Thandi Nkosi", role, pct, LocalDate.of(2024, 1, 1), null), USER);
    }

    @Test @DisplayName("a new director is an active person")
    void director() {
        ComplianceClientParty p = party("director", null);
        assertThat(p.getRole()).isEqualTo("DIRECTOR");
        assertThat(p.getPartyType()).isEqualTo("PERSON");
        assertThat(p.isActive()).isTrue();
    }

    @Test @DisplayName("a name and a known role are required")
    void required() {
        assertThatThrownBy(() -> ComplianceClientParty.create(TenantId.generate(), UUID.randomUUID(), d(" ", "DIRECTOR", null, null, null), USER))
                .hasMessageContaining("fullName");
        assertThatThrownBy(() -> party("CEO", null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ComplianceClientParty.create(TenantId.generate(), UUID.randomUUID(), d("A", null, null, null, null), USER))
                .hasMessageContaining("role");
    }

    @Test @DisplayName("an invalid SA ID number is refused")
    void badId() {
        assertThatThrownBy(() -> ComplianceClientParty.create(TenantId.generate(), UUID.randomUUID(),
                new ComplianceClientParty.Details("A", null, "SA_ID", "1234567890123", "DIRECTOR", null, null, null, null), USER))
                .hasMessageContaining("not valid");
    }

    @Test @DisplayName("a director cannot carry a percentage")
    void directorPercent() {
        assertThatThrownBy(() -> party("DIRECTOR", "10")).isInstanceOf(IllegalArgumentException.class);
        assertThat(party("SHAREHOLDER", "10").getOwnershipPercent()).isEqualTo(new BigDecimal("10"));
    }

    @Test @DisplayName("resigning ends the role, once, and not before appointment")
    void resign() {
        ComplianceClientParty p = party("DIRECTOR", null);
        assertThatThrownBy(() -> p.resign(LocalDate.of(2023, 1, 1), USER)).isInstanceOf(IllegalArgumentException.class);
        assertThat(p.isActive()).isTrue();
        p.resign(LocalDate.of(2025, 6, 30), USER);
        assertThat(p.isActive()).isFalse();
        assertThat(p.getResignedOn()).isEqualTo(LocalDate.of(2025, 6, 30));
        assertThatThrownBy(() -> p.resign(null, USER)).isInstanceOf(IllegalStateException.class);
    }

    @Test @DisplayName("a failed update leaves the record as it was")
    void failedUpdate() {
        ComplianceClientParty p = party("SHAREHOLDER", "30");
        assertThatThrownBy(() -> p.apply(d("Someone Else", "SHAREHOLDER", "150", null, null), USER)).isInstanceOf(IllegalArgumentException.class);
        assertThat(p.getFullName()).isEqualTo("Thandi Nkosi");
        assertThat(p.getOwnershipPercent()).isEqualTo(new BigDecimal("30"));
    }
}
