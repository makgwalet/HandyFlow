package za.co.handyflow.platform.legalpractice.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A per-matter deadline — court date, prescription deadline, filing
 * deadline, limitation period. A matter-scoped, structurally simpler
 * cousin of {@code legalcompliance}'s own tenant-wide deadline tracking
 * (that module tracks the TENANT's own regulatory obligations; this
 * tracks each CLIENT MATTER's own key dates — same underlying idea,
 * deliberately not shared code, since legalpractice has no dependency on
 * legalcompliance). Feeds {@code LpNotificationScheduler}'s daily sweep,
 * the same acknowledge-once-per-due-date shape {@code AgHealthEvent}/
 * {@code AgScoutingRecord} already established.
 */
@Entity
@Table(name = "lp_matter_key_dates")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LpMatterKeyDate {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "matter_id", nullable = false)
    private UUID matterId;

    @Column(name = "date_type", nullable = false, length = 30)
    private String dateType; // COURT_DATE | PRESCRIPTION_DEADLINE | FILING_DEADLINE | LIMITATION_PERIOD | OTHER

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(nullable = false)
    private String description;

    @Column(nullable = false)
    private boolean acknowledged = false;

    @Column(nullable = false, length = 20)
    private String status; // PENDING | COMPLETED | MISSED

    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    public static LpMatterKeyDate create(TenantId tenantId, UUID matterId, String dateType,
                                          LocalDate dueDate, String description, String notes) {
        LpMatterKeyDate k = new LpMatterKeyDate();
        k.tenantId = tenantId;
        k.matterId = matterId;
        k.dateType = dateType;
        k.dueDate = dueDate;
        k.description = description;
        k.notes = notes;
        k.acknowledged = false;
        k.status = "PENDING";
        k.createdAt = Instant.now();
        k.updatedAt = Instant.now();
        return k;
    }

    public void update(String dateType, LocalDate dueDate, String description, String notes) {
        this.dateType = dateType;
        this.dueDate = dueDate;
        this.description = description;
        this.notes = notes;
        this.acknowledged = false; // a changed date needs to be re-noticed, mirrors AgScoutingRecord.update()
        this.updatedAt = Instant.now();
    }

    public void complete() {
        this.status = "COMPLETED";
        this.updatedAt = Instant.now();
    }

    public void markMissed() {
        this.status = "MISSED";
        this.updatedAt = Instant.now();
    }

    public void acknowledge() {
        this.acknowledged = true;
        this.updatedAt = Instant.now();
    }

    @PreUpdate
    void onUpdate() { this.updatedAt = Instant.now(); }
}
