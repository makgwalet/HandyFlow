package za.co.handyflow.platform.training.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A scheduled running of a {@link TrainingCourse} — "First Aid Level 1,
 * 12–13 Sept 2026, Boardroom A". Capacity is enforced by the service
 * layer counting live {@link TrainingEnrollment} rows at enrol time
 * (deliberately not a denormalized {@code enrolledCount} column on this
 * entity — a live count avoids a second source of truth that could
 * drift from the enrollment table, the same reasoning
 * {@code WhseInventory}'s own Javadoc gives for centralizing its
 * mutation logic in one place rather than duplicating a tally
 * elsewhere).
 */
@Entity
@Table(name = "training_sessions")
@Getter
@NoArgsConstructor
public class TrainingSession {

    @Id UUID id;
    @Column(name = "tenant_id") UUID tenantId;

    @Column(name = "course_id") UUID courseId;

    @Column(name = "start_date") LocalDate startDate;
    @Column(name = "end_date") LocalDate endDate;

    String venue;
    @Column(name = "trainer_name") String trainerName;

    /** Null = unlimited. */
    Integer capacity;

    /** SCHEDULED | IN_PROGRESS | COMPLETED | CANCELLED */
    String status;

    String notes;
    @Column(name = "cancel_reason") String cancelReason;

    @Column(name = "created_at") Instant createdAt;
    @Column(name = "updated_at") Instant updatedAt;
    @Version long version;

    public static TrainingSession create(TenantId tenantId, UUID courseId, LocalDate startDate, LocalDate endDate,
                                          String venue, String trainerName, Integer capacity, String notes) {
        if (endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("endDate cannot be before startDate");
        }
        TrainingSession s = new TrainingSession();
        s.id = UUID.randomUUID();
        s.tenantId = tenantId.getValue();
        s.courseId = courseId;
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
