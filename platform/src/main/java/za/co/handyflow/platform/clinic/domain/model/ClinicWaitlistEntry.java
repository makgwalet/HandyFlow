package za.co.handyflow.platform.clinic.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

/**
 * FIX: "no waitlist" gap — cancellations/no-shows had no mechanism to
 * backfill from a waiting list, which most scheduling-heavy clinic tools
 * treat as core. practitionerId is nullable — a patient can wait for "any
 * available practitioner" or a specific one.
 */
@Entity
@Table(name = "clinic_waitlist_entries")
@Getter
@NoArgsConstructor
public class ClinicWaitlistEntry {

    @Id UUID id;
    @Column(name = "tenant_id")        UUID tenantId;
    @Column(name = "patient_id")       UUID patientId;
    @Column(name = "practitioner_id")  UUID practitionerId;
    @Column(name = "appointment_type") String appointmentType;
    String notes;
    String status = "WAITING"; // WAITING | CONTACTED | SCHEDULED | CANCELLED
    @Column(name = "created_at") Instant createdAt;
    @Column(name = "updated_at") Instant updatedAt;

    public static ClinicWaitlistEntry create(TenantId tenantId, UUID patientId, UUID practitionerId,
                                             String appointmentType, String notes) {
        ClinicWaitlistEntry w = new ClinicWaitlistEntry();
        w.id = UUID.randomUUID();
        w.tenantId = tenantId.getValue();
        w.patientId = patientId;
        w.practitionerId = practitionerId;
        w.appointmentType = appointmentType;
        w.notes = notes;
        w.status = "WAITING";
        w.createdAt = Instant.now();
        w.updatedAt = Instant.now();
        return w;
    }

    public void markContacted() { this.status = "CONTACTED"; this.updatedAt = Instant.now(); }
    public void markScheduled() { this.status = "SCHEDULED"; this.updatedAt = Instant.now(); }
    public void cancel()        { this.status = "CANCELLED"; this.updatedAt = Instant.now(); }
}