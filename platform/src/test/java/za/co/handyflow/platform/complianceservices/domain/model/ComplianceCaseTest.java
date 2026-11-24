package za.co.handyflow.platform.complianceservices.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ComplianceCaseTest {

    private static final TenantId T = TenantId.generate();
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 24);
    private static final UUID ME = UUID.randomUUID();
    private static final UUID OTHER = UUID.randomUUID();

    private static ComplianceCase open(UUID owner) {
        return ComplianceCase.open(T, UUID.randomUUID(), "Fix the information officer", null, null, owner, TODAY.plusDays(10), null, null, null, ME, TODAY);
    }

    @Test @DisplayName("a new case is open with a default severity")
    void opened() {
        var c = open(ME);
        assertThat(c.getStatus()).isEqualTo("OPEN");
        assertThat(c.getSeverity()).isEqualTo("MEDIUM");
        assertThat(c.isLive()).isTrue();
    }

    @Test @DisplayName("work cannot start without an owner")
    void needsOwner() {
        assertThatThrownBy(() -> open(null).start()).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("owner");
    }

    @Test @DisplayName("the normal path runs open, in progress, waiting, back, resolved, closed by another person")
    void path() {
        var c = open(ME);
        c.start();
        c.awaitClient();
        c.resume();
        c.resolve(ME, "Officer appointed and registered with the regulator", 0);
        assertThat(c.getStatus()).isEqualTo("RESOLVED");
        assertThat(c.getResolvedBy()).isEqualTo(ME);
        c.close(OTHER, null);
        assertThat(c.getStatus()).isEqualTo("CLOSED");
        assertThat(c.getCloseExceptionReason()).isNull();
        assertThat(c.isLive()).isFalse();
    }

    @Test @DisplayName("resolving is refused with open steps or no note")
    void resolveGuards() {
        var c = open(ME);
        c.start();
        assertThatThrownBy(() -> c.resolve(ME, "Officer appointed and registered", 1)).hasMessageContaining("still open");
        assertThatThrownBy(() -> c.resolve(ME, "done", 0)).hasMessageContaining("how it was resolved");
        assertThat(c.getStatus()).isEqualTo("IN_PROGRESS");
    }

    @Test @DisplayName("the same person closing needs a reason and it is kept")
    void sameCloser() {
        var c = open(ME);
        c.start();
        c.resolve(ME, "Officer appointed and registered", 0);
        assertThatThrownBy(() -> c.close(ME, null)).hasMessageContaining("someone else");
        assertThat(c.getStatus()).isEqualTo("RESOLVED");
        c.close(ME, "Sole practitioner, nobody else to check");
        assertThat(c.getCloseExceptionReason()).isEqualTo("Sole practitioner, nobody else to check");
    }

    @Test @DisplayName("reopening clears the resolution and needs a reason")
    void reopen() {
        var c = open(ME);
        c.start();
        c.resolve(ME, "Officer appointed and registered", 0);
        assertThatThrownBy(() -> c.reopen("no")).isInstanceOf(IllegalArgumentException.class);
        c.reopen("The registration was rejected by the regulator");
        assertThat(c.getStatus()).isEqualTo("IN_PROGRESS");
        assertThat(c.getResolutionNote()).isNull();
        assertThat(c.getResolvedBy()).isNull();
    }

    @Test @DisplayName("cancelling keeps the reason, and a finished case cannot move again or be reassigned")
    void cancel() {
        var c = open(ME);
        assertThatThrownBy(() -> c.cancel("short")).isInstanceOf(IllegalArgumentException.class);
        c.cancel("The client has left the practice");
        assertThat(c.getStatus()).isEqualTo("CANCELLED");
        assertThat(c.getCancelReason()).isEqualTo("The client has left the practice");
        assertThatThrownBy(c::start).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> c.assign(OTHER)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> c.reschedule(TODAY.plusDays(1), TODAY)).isInstanceOf(IllegalStateException.class);
    }

    @Test @DisplayName("resume only works from waiting on the client, and a past due date is refused")
    void misc() {
        var c = open(ME);
        c.start();
        assertThatThrownBy(c::resume).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> c.reschedule(TODAY.minusDays(1), TODAY)).isInstanceOf(IllegalArgumentException.class);
        c.reschedule(TODAY.plusDays(5), TODAY);
        assertThat(c.getDueDate()).isEqualTo(TODAY.plusDays(5));
        c.assign(OTHER);
        assertThat(c.getOwnerId()).isEqualTo(OTHER);
    }

    @Test @DisplayName("steps are completed once, cancelled with a reason, and counted as open only while to do")
    void steps() {
        var s = ComplianceCaseStep.of(T, UUID.randomUUID(), "Collect the ID copy", null, 0, TODAY);
        assertThat(s.isOpen()).isTrue();
        s.complete(ME);
        assertThat(s.isOpen()).isFalse();
        assertThat(s.getDoneBy()).isEqualTo(ME);
        assertThatThrownBy(() -> s.complete(ME)).isInstanceOf(IllegalStateException.class);
        var t = ComplianceCaseStep.of(T, UUID.randomUUID(), "Call the client", TODAY, 1, TODAY);
        assertThatThrownBy(() -> t.cancel("no")).isInstanceOf(IllegalArgumentException.class);
        t.cancel("The client sent it already by email");
        assertThat(t.getStatus()).isEqualTo("CANCELLED");
        assertThatThrownBy(() -> ComplianceCaseStep.of(T, UUID.randomUUID(), "x", null, 0, TODAY)).isInstanceOf(IllegalArgumentException.class);
    }
}
