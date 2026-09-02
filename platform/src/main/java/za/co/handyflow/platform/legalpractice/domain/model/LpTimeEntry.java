package za.co.handyflow.platform.legalpractice.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A single billable (or non-billable) block of attorney time against a
 * matter. A DIRECT PORT of {@code accountant.TimeEntry}'s own lifecycle
 * and calculation shape — confirmed by direct source read before writing
 * this: UNBILLED -&gt; BILLED -&gt; WRITTEN_OFF/NON_BILLABLE,
 * {@code lineTotal()} = hours &times; hourlyRate rounded HALF_UP,
 * {@code isEditable()} false once BILLED, {@code markBilled(invoiceId)}
 * throws if not currently UNBILLED. {@code hourlyRate} is snapshotted
 * onto the entry at creation time (from the attorney's own default rate,
 * or a matter-specific override the caller supplies) rather than read
 * live from {@code LpAttorney} later, so a rate change never rewrites
 * history — same reasoning TimeEntry's own snapshot already applies.
 */
@Entity
@Table(name = "lp_time_entries")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LpTimeEntry {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "matter_id", nullable = false)
    private UUID matterId;

    @Column(name = "attorney_id", nullable = false)
    private UUID attorneyId;

    @Column(name = "entry_date", nullable = false)
    private LocalDate entryDate;

    @Column(nullable = false, precision = 8, scale = 2)
    private BigDecimal hours;

    @Column(name = "hourly_rate", nullable = false, precision = 12, scale = 2)
    private BigDecimal hourlyRate;

    @Column(nullable = false)
    private String description;

    @Column(nullable = false)
    private boolean billable = true;

    @Column(nullable = false, length = 20)
    private String status; // UNBILLED | BILLED | WRITTEN_OFF | NON_BILLABLE

    @Column(name = "invoice_id")
    private UUID invoiceId; // set by markBilled() — the LpInvoice this entry was billed on

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    public static LpTimeEntry create(TenantId tenantId, UUID matterId, UUID attorneyId, LocalDate entryDate,
                                      BigDecimal hours, BigDecimal hourlyRate, String description, boolean billable) {
        LpTimeEntry t = new LpTimeEntry();
        t.tenantId = tenantId;
        t.matterId = matterId;
        t.attorneyId = attorneyId;
        t.entryDate = entryDate != null ? entryDate : LocalDate.now();
        t.hours = hours;
        t.hourlyRate = hourlyRate;
        t.description = description;
        t.billable = billable;
        t.status = billable ? "UNBILLED" : "NON_BILLABLE";
        t.createdAt = Instant.now();
        t.updatedAt = Instant.now();
        return t;
    }

    public BigDecimal lineTotal() {
        return hours.multiply(hourlyRate).setScale(2, RoundingMode.HALF_UP);
    }

    public boolean isEditable() {
        return "UNBILLED".equals(this.status);
    }

    public void update(LocalDate entryDate, BigDecimal hours, BigDecimal hourlyRate, String description) {
        if (!isEditable()) {
            throw new IllegalStateException("Time entry is not editable in status " + this.status);
        }
        this.entryDate = entryDate;
        this.hours = hours;
        this.hourlyRate = hourlyRate;
        this.description = description;
        this.updatedAt = Instant.now();
    }

    public void markBilled(UUID invoiceId) {
        if (!"UNBILLED".equals(this.status)) {
            throw new IllegalStateException("Only an UNBILLED time entry can be billed, current status: " + this.status);
        }
        this.status = "BILLED";
        this.invoiceId = invoiceId;
        this.updatedAt = Instant.now();
    }

    public void writeOff() {
        if (!"UNBILLED".equals(this.status)) {
            throw new IllegalStateException("Only an UNBILLED time entry can be written off, current status: " + this.status);
        }
        this.status = "WRITTEN_OFF";
        this.updatedAt = Instant.now();
    }

    @PreUpdate
    void onUpdate() { this.updatedAt = Instant.now(); }
}
