package za.co.handyflow.platform.trainingprovider.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A scheduled running of a {@link TrainProvCourse}. Two shapes,
 * distinguished by {@code sessionType}:
 * <ul>
 *   <li>{@code PUBLIC} — open enrollment, {@code clientId} is null,
 *       and delegates enrolling can come from any number of different
 *       clients (each enrollment carries its own client reference via
 *       its delegate).</li>
 *   <li>{@code CLOSED} — an in-house session run exclusively for one
 *       client, {@code clientId} is required. The service layer (not
 *       a DB constraint) rejects an enrollment whose delegate belongs
 *       to a different client than the session's own {@code clientId}
 *       — flagged as a service-layer rule, not enforced at the schema
 *       level, in the accompanying status doc.</li>
 * </ul>
 */
@Entity
@Table(name = "trainprov_sessions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TrainProvSession {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "course_id", nullable = false)
    private UUID courseId;

    /** PUBLIC | CLOSED */
    @Column(name = "session_type", nullable = false)
    private String sessionType;

    /** Required when sessionType = CLOSED; must be null when PUBLIC. */
    @Column(name = "client_id")
    private UUID clientId;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    private String venue;

    @Column(name = "trainer_name")
    private String trainerName;

    private Integer capacity;

    /** SCHEDULED | IN_PROGRESS | COMPLETED | CANCELLED */
    private String status;

    private String notes;

    @Column(name = "cancel_reason")
    private String cancelReason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    public static TrainProvSession create(TenantId tenantId, UUID courseId, String sessionType, UUID clientId,
                                           LocalDate startDate, LocalDate endDate, String venue,
                                           String trainerName, Integer capacity, String notes) {
        if (endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("endDate cannot be before startDate");
        }
        if ("CLOSED".equals(sessionType) && clientId == null) {
            throw new IllegalArgumentException("A CLOSED session requires a clientId");
        }
        if ("PUBLIC".equals(sessionType) && clientId != null) {
            throw new IllegalArgumentException("A PUBLIC session cannot be tied to a single clientId");
        }
        TrainProvSession s = new TrainProvSession();
        s.tenantId = tenantId.getValue();
        s.courseId = courseId;
        s.sessionType = sessionType;
        s.clientId = clientId;
        s.startDate = startDate;
        s.endDate = endDate;
        s.venue = venue;
        s.trainerName = trainerName;
        s.capacity = capacity;
        s.notes = notes;
        s.status = "SCHEDULED";
        s.createdAt = Instant.now();
        s.updatedAt = Instant.now();
        return s;
    }

    public boolean isClosed() {
        return "CLOSED".equals(this.sessionType);
    }

    public void reschedule(LocalDate newStart, LocalDate newEnd) {
        requireNotTerminal();
        if (newEnd.isBefore(newStart)) throw new IllegalArgumentException("endDate cannot be before startDate");
        this.startDate = newStart;
        this.endDate = newEnd;
        this.updatedAt = Instant.now();
    }

    public void update(String venue, String trainerName, Integer capacity, String notes) {
        requireNotTerminal();
        this.venue = venue;
        this.trainerName = trainerName;
        this.capacity = capacity;
        this.notes = notes;
        this.updatedAt = Instant.now();
    }

    public void start() {
        if (!"SCHEDULED".equals(this.status)) {
            throw new IllegalStateException("Only a SCHEDULED session can start (current: " + this.status + ")");
        }
        this.status = "IN_PROGRESS";
        this.updatedAt = Instant.now();
    }

    public void complete() {
        if (!"SCHEDULED".equals(this.status) && !"IN_PROGRESS".equals(this.status)) {
            throw new IllegalStateException("Only a SCHEDULED or IN_PROGRESS session can complete (current: " + this.status + ")");
        }
        this.status = "COMPLETED";
        this.updatedAt = Instant.now();
    }

    public void cancel(String reason) {
        requireNotTerminal();
        this.status = "CANCELLED";
        this.cancelReason = reason;
        this.updatedAt = Instant.now();
    }

    public boolean isFull(long currentEnrolledCount) {
        return this.capacity != null && currentEnrolledCount >= this.capacity;
    }

    public boolean acceptsEnrollment() {
        return "SCHEDULED".equals(this.status) || "IN_PROGRESS".equals(this.status);
    }

    private void requireNotTerminal() {
        if ("COMPLETED".equals(this.status) || "CANCELLED".equals(this.status)) {
            throw new IllegalStateException("Session is already " + this.status);
        }
    }
}
