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
    // S1-8: front-desk / nurse progress through the clinic, for waiting-time reporting.
    @Column(name = "checked_in_at") Instant checkedInAt;
    @Column(name = "triaged_at")    Instant triagedAt;
    @Column(name = "room_id") UUID roomId;
    @Version long version;

    // ── Factory ───────────────────────────────────────────────────────────────

    public static ClinicAppointment create(TenantId tenantId,
                                           UUID patientId, UUID practitionerId,
                                           Instant scheduledAt, int durationMinutes,
                                           String appointmentType, String reason) {
        return create(tenantId, patientId, practitionerId, scheduledAt, durationMinutes, appointmentType, reason, null);
    }

    public static ClinicAppointment create(TenantId tenantId,
                                           UUID patientId, UUID practitionerId,
                                           Instant scheduledAt, int durationMinutes,
                                           String appointmentType, String reason, UUID roomId) {
        ClinicAppointment a = new ClinicAppointment();
        a.roomId          = roomId;
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

    /** Patient has arrived. */
    public void checkIn() {
        if (!"SCHEDULED".equals(status) && !"CONFIRMED".equals(status)) {
            throw new IllegalStateException("Check-in is only valid for SCHEDULED or CONFIRMED appointments (is " + status + ").");
        }
        this.status      = "CHECKED_IN";
        this.checkedInAt = Instant.now();
        this.updatedAt   = this.checkedInAt;
    }

    /** Nurse has done the initial assessment. */
    public void triage() {
        requireStatus("CHECKED_IN");
        this.status    = "TRIAGED";
        this.triagedAt = Instant.now();
        this.updatedAt = this.triagedAt;
    }

    /** IN_PROGRESS is the in-consultation state. A patient may be seen straight from CONFIRMED, CHECKED_IN or TRIAGED. */
    public void start() {
        if (!"CONFIRMED".equals(status) && !"CHECKED_IN".equals(status) && !"TRIAGED".equals(status)) {
            throw new IllegalStateException(
                    "An appointment can only be started from CONFIRMED, CHECKED_IN or TRIAGED (is " + status + ").");
        }
        this.status    = "IN_PROGRESS";
        this.updatedAt = Instant.now();
    }

    /**
     * The consultation was thrown away before anything was signed: the patient is still in the building, so the
     * appointment goes back to CHECKED_IN rather than staying "in consultation". Any other state is left alone.
     */
    public void returnToWaiting() {
        if (!"IN_PROGRESS".equals(status)) return;
        this.status    = "CHECKED_IN";
        this.updatedAt = Instant.now();
    }

    public void complete() {
        if (!"IN_PROGRESS".equals(this.status) && !"CONFIRMED".equals(this.status)
                && !"SCHEDULED".equals(this.status) && !"CHECKED_IN".equals(this.status)
                && !"TRIAGED".equals(this.status)) {
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
            throw new IllegalStateException("No-show only valid for SCHEDULED or CONFIRMED (a patient who has arrived is not a no-show)");
        this.status    = "NO_SHOW";
        this.updatedAt = Instant.now();
    }

    /**
     * Moves a booking that has not started yet. A patient who has already arrived is here, so there is
     * nothing to move. The booking goes back to SCHEDULED (an earlier confirmation was for the old time)
     * and becomes eligible for a fresh automatic reminder.
     */
    public void reschedule(Instant newTime, int minutes, UUID newPractitionerId) {
        reschedule(newTime, minutes, newPractitionerId, null);
    }

    /** A null practitioner or room leaves it as it is. */
    public void reschedule(Instant newTime, int minutes, UUID newPractitionerId, UUID newRoomId) {
        if (!"SCHEDULED".equals(this.status) && !"CONFIRMED".equals(this.status))
            throw new IllegalStateException("Only a SCHEDULED or CONFIRMED appointment can be moved (is " + this.status + ")");
        this.scheduledAt = newTime;
        this.durationMinutes = minutes;
        if (newPractitionerId != null) this.practitionerId = newPractitionerId;
        if (newRoomId != null) this.roomId = newRoomId;
        this.status = "SCHEDULED";
        this.reminderSentAt = null;
        this.updatedAt = Instant.now();
    }

    /** Takes the appointment out of its room. */
    public void clearRoom() {
        this.roomId = null;
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