package za.co.handyflow.platform.complianceservices.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClientComplianceDeadlineTest {

    private static final TenantId TENANT = TenantId.generate();
    private static final UUID USER = UUID.randomUUID();

    private static ClientComplianceDeadline generated() {
        return ClientComplianceDeadline.generated(TENANT, UUID.randomUUID(), UUID.randomUUID(), 2, "CONSULTANT", "CIPC-AR - Annual return", LocalDate.of(2027, 1, 31), USER);
    }

    @Test @DisplayName("a generated deadline remembers its obligation and version")
    void remembers() {
        var d = generated();
        assertThat(d.isGenerated()).isTrue();
        assertThat(d.getObligationVersionNumber()).isEqualTo(2);
        assertThat(d.getDeadlineType()).isEqualTo("OBLIGATION");
        assertThat(d.getResponsibleRole()).isEqualTo("CONSULTANT");
        assertThat(d.getStatus()).isEqualTo("PENDING");
    }

    @Test @DisplayName("cancelling needs a reason and keeps the deadline")
    void cancels() {
        var d = generated();
        assertThatThrownBy(() -> d.cancel("  ", USER)).isInstanceOf(IllegalArgumentException.class);
        d.cancel("The due rule changed", USER);
        assertThat(d.getStatus()).isEqualTo("CANCELLED");
        assertThat(d.getCancelledReason()).isEqualTo("The due rule changed");
    }

    @Test @DisplayName("only a pending deadline can be cancelled, and a cancelled one cannot be marked done")
    void order() {
        var done = generated();
        done.markDone(USER);
        assertThatThrownBy(() -> done.cancel("reason", USER)).isInstanceOf(IllegalStateException.class);
        var gone = generated();
        gone.cancel("reason", USER);
        assertThatThrownBy(() -> gone.markDone(USER)).isInstanceOf(IllegalStateException.class);
    }

    @Test @DisplayName("a cancelled deadline is never overdue or due soon")
    void cancelledQuiet() {
        var d = ClientComplianceDeadline.generated(TENANT, UUID.randomUUID(), UUID.randomUUID(), 1, null, "x", LocalDate.now().minusDays(3), USER);
        assertThat(d.isOverdue()).isTrue();
        d.cancel("no longer applies", USER);
        assertThat(d.isOverdue()).isFalse();
        assertThat(d.isDueWithin(14)).isFalse();
    }
}
