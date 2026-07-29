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
    @Column(name = "tenant_id")       UUID   tenantId;
    @Column(name = "patient_id")      UUID   patientId;
    @Column(name = "practitioner_id") UUID   practitionerId;
    @Column(name = "scheduled_at")    Instant scheduledAt;
    @Column(name = "duration_minutes") int   durationMinutes = 30;
    @Column(name = "appointment_type") String appointmentType;
    String status;
    String reason;
    String notes;
    @Column(name = "created_at") Instant createdAt;
    @Column(name = "updated_at") Instant updatedAt;
    @Column(name = "deleted_at") Instant deletedAt;
    @Column(name = "deleted_by") UUID    deletedBy;
    // FIX: "no appointment reminders" gap — the single highest-value gap the
    // audit flagged. Idempotency guard: once set, the reminder scheduler
    // never re-sends for this appointment.
    @Column(name = "reminder_sent_at") Instant reminderSentAt;
    // FIX: "no telehealth/video consultation option" gap — set once a video
    // room has been created for this appointment (see
    // ClinicTelehealthService). Null for any in-person appointment.
    @Column(name = "video_room_url") String videoRoomUrl;
    @Version long version;

    // ── Factory ───────────────────────────────────────────────────────────────

    public static ClinicAppointment create(TenantId tenantId,
                                           UUID patientId, UUID practitionerId,
                                           Instant scheduledAt, int durationMinutes,
                                           String appointmentType, String reason) {
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

    // ── Status transitions ────────────────────────────────────────────────────
    // WHY explicit methods instead of a setStatus()?
    // Business rules live in the domain. A caller cannot accidentally put an
    // appointment into IN_PROGRESS from COMPLETED — only valid transitions exist.

    public void confirm() {
        requireStatus("SCHEDULED");
        this.status    = "CONFIRMED";
        this.updatedAt = Instant.now();
    }

    public void start() {
        requireStatus("CONFIRMED");
        this.status    = "IN_PROGRESS";
        this.updatedAt = Instant.now();
    }

    public void complete() {
        if (!"IN_PROGRESS".equals(this.status) && !"CONFIRMED".equals(this.status)
                && !"SCHEDULED".equals(this.status)) {
            throw new IllegalStateException(
                    "Cannot complete appointment in status: " + this.status);
        }
        this.status    = "COMPLETED";
        this.updatedAt = Instant.now();
    }

    public void cancel() {
        if ("COMPLETED".equals(this.status))
            throw new IllegalStateException("Cannot cancel a completed appointment");
        this.status    = "CANCELLED";
        this.updatedAt = Instant.now();
    }

    public void noShow() {
        if (!"SCHEDULED".equals(this.status) && !"CONFIRMED".equals(this.status))
            throw new IllegalStateException("No-show only valid for SCHEDULED or CONFIRMED");
        this.status    = "NO_SHOW";
        this.updatedAt = Instant.now();
    }

    public boolean isActive() {
        return !"CANCELLED".equals(this.status) && !"COMPLETED".equals(this.status)
                && !"NO_SHOW".equals(this.status) && this.deletedAt == null;
    }

    /** Idempotency guard for the reminder scheduler — see reminderSentAt. */
    public void markReminderSent() {
        this.reminderSentAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    /** Called once, when a video room is first created for this appointment. */
    public void assignVideoRoom(String url) {
        this.videoRoomUrl = url;
        this.updatedAt = Instant.now();
    }

    private void requireStatus(String expected) {
        if (!expected.equals(this.status))
            throw new IllegalStateException(
                    "Expected status " + expected + " but was " + this.status);
    }
}