package za.co.handyflow.platform.clinic.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "clinic_appointments")
@Getter
@NoArgsConstructor
public class ClinicAppointment {

    @Id UUID id;
    @Column(name = "tenant_id")       UUID tenantId;
    @Column(name = "patient_id")      UUID patientId;
    @Column(name = "practitioner_id") UUID practitionerId;
    @Column(name = "scheduled_at")    Instant scheduledAt;
    @Column(name = "duration_minutes") int durationMinutes = 30;
    @Column(name = "appointment_type") String appointmentType;
    String status;
    String reason;
    String notes;
    @Column(name = "created_at") Instant createdAt;
    @Column(name = "updated_at") Instant updatedAt;
    @Column(name = "deleted_at") Instant deletedAt;
    @Column(name = "deleted_by") UUID deletedBy;
    @Version long version;

    public static ClinicAppointment create(TenantId tenantId, UUID patientId,
                                           UUID practitionerId, Instant scheduledAt,
                                           int durationMinutes, String appointmentType,
                                           String reason) {
        ClinicAppointment a = new ClinicAppointment();
        a.id              = UUID.randomUUID();
        a.tenantId        = tenantId.getValue();
        a.patientId       = patientId;
        a.practitionerId  = practitionerId;
        a.scheduledAt     = scheduledAt;
        a.durationMinutes = durationMinutes;
        a.appointmentType = appointmentType != null ? appointmentType : "CONSULTATION";
        a.status          = "SCHEDULED";
        a.reason          = reason;
        a.createdAt       = Instant.now();
        a.updatedAt       = Instant.now();
        return a;
    }

    public void confirm()    { this.status = "CONFIRMED"; this.updatedAt = Instant.now(); }
    public void start()      { this.status = "IN_PROGRESS"; this.updatedAt = Instant.now(); }
    public void complete()   { this.status = "COMPLETED"; this.updatedAt = Instant.now(); }
    public void cancel()     { this.status = "CANCELLED"; this.updatedAt = Instant.now(); }
    public void noShow()     { this.status = "NO_SHOW"; this.updatedAt = Instant.now(); }

    public void softDelete(UUID deletedBy) {
        this.deletedAt = Instant.now();
        this.deletedBy = deletedBy;
        this.updatedAt = Instant.now();
    }
}