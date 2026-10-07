package za.co.handyflow.platform.clinic.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClinicAppointmentProgressTest {

    private ClinicAppointment scheduled() {
        TenantId tenant = Mockito.mock(TenantId.class);
        Mockito.when(tenant.getValue()).thenReturn(UUID.randomUUID());
        return ClinicAppointment.create(tenant, UUID.randomUUID(), null, Instant.now(), 30, "CONSULTATION", null);
    }

    @Test
    @DisplayName("scheduled -> checked in -> triaged -> in consultation -> completed")
    void fullFlow() {
        ClinicAppointment a = scheduled();

        a.checkIn();
        assertThat(a.getStatus()).isEqualTo("CHECKED_IN");
        assertThat(a.getCheckedInAt()).isNotNull();

        a.triage();
        assertThat(a.getStatus()).isEqualTo("TRIAGED");
        assertThat(a.getTriagedAt()).isNotNull();

        a.start();
        assertThat(a.getStatus()).isEqualTo("IN_PROGRESS");

        a.complete();
        assertThat(a.getStatus()).isEqualTo("COMPLETED");
        assertThat(a.isActive()).isFalse();
    }

    @Test
    @DisplayName("a patient who has arrived can be seen without triage, and still counts as active")
    void startFromCheckedIn() {
        ClinicAppointment a = scheduled();
        a.checkIn();
        assertThat(a.isActive()).isTrue();

        a.start();

        assertThat(a.getStatus()).isEqualTo("IN_PROGRESS");
    }

    @Test
    @DisplayName("the old path still works: confirm then start")
    void legacyPath() {
        ClinicAppointment a = scheduled();
        a.confirm();
        a.start();
        assertThat(a.getStatus()).isEqualTo("IN_PROGRESS");
    }

    @Test
    @DisplayName("invalid moves are rejected")
    void invalidMoves() {
        ClinicAppointment a = scheduled();
        assertThatThrownBy(a::triage).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(a::start).isInstanceOf(IllegalStateException.class);   // SCHEDULED cannot start

        a.checkIn();
        assertThatThrownBy(a::checkIn).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(a::noShow).isInstanceOf(IllegalStateException.class).hasMessageContaining("has arrived");
        assertThatThrownBy(a::confirm).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("an arrived patient can leave: cancel is allowed, complete is allowed")
    void cancelAfterArrival() {
        ClinicAppointment a = scheduled();
        a.checkIn();
        a.triage();
        a.cancel();
        assertThat(a.getStatus()).isEqualTo("CANCELLED");
    }
}
