package za.co.handyflow.platform.accountant.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity(name = "AccountantTimeEntry")
@Table(name = "acc_time_entries")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TimeEntry {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "tenant_id",       nullable = false) private UUID       tenantId;
    @Column(name = "client_id",       nullable = false) private UUID       clientId;
    @Column(name = "practitioner_id")                   private UUID       practitionerId;
    // NEW: closes the "staff-level time report" gap. See the migration's
    // own comment — no backfill for existing rows, deliberately.
    @Column(name = "practitioner_name", length = 255)    private String     practitionerName;
    @Column(name = "entry_date",      nullable = false) private LocalDate  entryDate;
    @Column(name = "activity_type",   nullable = false) private String     activityType;
    @Column(name = "description",     columnDefinition = "TEXT") private String description;

    @Column(name = "hours", nullable = false, precision = 5, scale = 2)
    private BigDecimal hours;

    @Column(name = "hourly_rate", nullable = false, precision = 10, scale = 2)
    private BigDecimal hourlyRate;

    @Column(name = "billable",  nullable = false) private boolean billable = true;
    @Column(name = "status",    nullable = false) private String  status   = "UNBILLED";
    @Column(name = "invoice_id")                  private UUID    invoiceId;

    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false)                     private Instant updatedAt;

    // ── Factory ───────────────────────────────────────────────────────────────

    public static TimeEntry create(UUID tenantId, UUID clientId, UUID practitionerId, String practitionerName,
                                   LocalDate entryDate, String activityType, String description,
                                   BigDecimal hours, BigDecimal hourlyRate, boolean billable) {
        TimeEntry t = new TimeEntry();
        t.tenantId         = tenantId;
        t.clientId         = clientId;
        t.practitionerId   = practitionerId;
        t.practitionerName = practitionerName;
        t.entryDate        = entryDate;
        t.activityType     = activityType;
        t.description      = description;
        t.hours            = hours;
        t.hourlyRate       = hourlyRate;
        t.billable         = billable;
        t.status           = billable ? "UNBILLED" : "NON_BILLABLE";
        t.createdAt        = Instant.now();
        t.updatedAt        = Instant.now();
        return t;
    }

    // ── Computed ──────────────────────────────────────────────────────────────

    /** Line total = hours × hourly rate, rounded to 2 decimal places. */
    public BigDecimal lineTotal() {
        return hours.multiply(hourlyRate).setScale(2, RoundingMode.HALF_UP);
    }

    // ── State machine ─────────────────────────────────────────────────────────

    public void markBilled(UUID invoiceId) {
        if (!"UNBILLED".equals(status))
            throw new IllegalStateException("Only UNBILLED entries can be billed. Current: " + status);
        this.status    = "BILLED";
        this.invoiceId = invoiceId;
        this.updatedAt = Instant.now();
    }

    /**
     * NEW: closes the accountant module audit's "time entry edit/delete"
     * gap. A BILLED entry's hours/rate were already copied into a real
     * FeeNoteLine at generation time (FeeNoteLine.forTimeEntry()) — an
     * invoice built from those numbers may already be sitting in a
     * client's inbox. Editing the source entry after the fact would
     * silently diverge from what was actually invoiced, and deleting it
     * would leave that FeeNoteLine's timeEntryId pointing at nothing.
     * Same discipline markBilled() above already enforces in the other
     * direction (only UNBILLED can become BILLED); this is the mirror
     * guard for going backwards.
     */
    public boolean isEditable() {
        return !"BILLED".equals(status);
    }

    public void update(LocalDate entryDate, String activityType, String description,
                       BigDecimal hours, BigDecimal hourlyRate, boolean billable) {
        if (!isEditable()) {
            throw new IllegalStateException(
                    "Cannot edit a time entry that has already been billed"
                            + (invoiceId != null ? " (invoice " + invoiceId + ")" : ""));
        }
        this.entryDate    = entryDate;
        this.activityType = activityType;
        this.description  = description;
        this.hours        = hours;
        this.hourlyRate   = hourlyRate;
        this.billable     = billable;
        // Status only re-derives from billable when the entry hasn't
        // been explicitly written off — editing content shouldn't
        // silently un-write-off something someone deliberately decided
        // not to bill.
        if (!"WRITTEN_OFF".equals(this.status)) {
            this.status = billable ? "UNBILLED" : "NON_BILLABLE";
        }
        this.updatedAt = Instant.now();
    }

    public void writeOff() {
        this.status    = "WRITTEN_OFF";
        this.updatedAt = Instant.now();
    }

    @PreUpdate
    void onUpdate() { this.updatedAt = Instant.now(); }
}