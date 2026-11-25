package za.co.handyflow.platform.trainingprovider.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

/** One line of an appointment's history. Written once, never changed. */
@Entity(name = "TrainProvPractitionerAppointmentEvent")
@Table(name = "trainprov_practitioner_appointment_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TrainProvPractitionerAppointmentEvent {
    @Id
    private UUID id = UUID.randomUUID();
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;
    @Column(name = "appointment_id", nullable = false, updatable = false)
    private UUID appointmentId;
    @Column(nullable = false, updatable = false)
    private String action;
    @Column(updatable = false)
    private String note;
    @Column(name = "acted_by", nullable = false, updatable = false)
    private UUID actedBy;
    @Column(name = "acted_at", nullable = false, updatable = false)
    private Instant actedAt;

    public static TrainProvPractitionerAppointmentEvent of(TenantId t, UUID appointmentId, String action, String note, UUID by) {
        TrainProvPractitionerAppointmentEvent e = new TrainProvPractitionerAppointmentEvent();
        e.tenantId = t.getValue();
        e.appointmentId = appointmentId;
        e.action = action;
        e.note = note == null || note.isBlank() ? null : note.trim();
        e.actedBy = by;
        e.actedAt = Instant.now();
        return e;
    }
}
