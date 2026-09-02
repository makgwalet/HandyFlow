package za.co.handyflow.platform.bookkeeping.domain.model;

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
 * Staff hours logged against a client — mirrors {@code
 * accountant.TimeEntry} almost exactly, including its UNBILLED ->
 * BILLED/WRITTEN_OFF/NON_BILLABLE lifecycle and billed-is-locked
 * editing guard. Only used for a client under a TIME_AND_MATERIALS
 * {@link BkServiceAgreement} — a RETAINER client's invoice never
 * touches time entries (see {@code BkBillingService}).
 */
@Entity
@Table(name = "bk_time_entries")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BkTimeEntry {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(name = "practitioner_id")
    private UUID practitionerId;
    @Column(name = "practitioner_name")
    private String practitionerName;

    @Column(name = "entry_date", nullable = false)
    private LocalDate entryDate;

    @Column(name = "activity_type", nullable = false)
    private String activityType;

    private String description;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal hours;

    @Column(name = "hourly_rate", nullable = false, precision = 10, scale = 2)
    private BigDecimal hourlyRate;

    @Column(nullable = false)
    private boolean billable = true;

    @Column(nullable = false)
    private String status = "UNBILLED"; // UNBILLED, BILLED, WRITTEN_OFF, NON_BILLABLE

    @Column(name = "invoice_id")
    private UUID invoiceId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static BkTimeEntry create(TenantId tenantId, UUID clientId, UUID practitionerId, String practitionerName,
                                      LocalDate entryDate, String activityType, String description,
                                      BigDecimal hours, BigDecimal hourlyRate, boolean billable) {
        BkTimeEntry t = new BkTimeEntry();
        t.tenantId = tenantId;
        t.clientId = clientId;
        t.practitionerId = practitionerId;
        t.practitionerName = practitionerName;
        t.entryDate = entryDate;
        t.activityType = activityType;
        t.description = description;
        t.hours = hours;
        t.hourlyRate = hourlyRate;
        t.billable = billable;
        t.status = billable ? "UNBILLED" : "NON_BILLABLE";
        t.createdAt = Instant.now();
        t.updatedAt = Instant.now();
        return t;
    }

    /** hours × hourlyRate, rounded to 2dp — same as accountant.TimeEntry.lineTotal(). */
    public BigDecimal lineTotal() {
        return hours.multiply(hourlyRate).setScale(2, RoundingMode.HALF_UP);
    }

    public boolean isEditable() { return !"BILLED".equals(status); }

    public void update(LocalDate entryDate, String activityType, String description,
                        BigDecimal hours, BigDecimal hourlyRate, boolean billable) {
        if (!isEditable())
            throw new IllegalStateException("Cannot edit a time entry that has already been billed"
                    + (invoiceId != null ? " (invoice " + invoiceId + ")" : ""));
        this.entryDate = entryDate;
        this.activityType = activityType;
        this.description = description;
        this.hours = hours;
        this.hourlyRate = hourlyRate;
        this.billable = billable;
        if (!"WRITTEN_OFF".equals(this.status)) {
            this.status = billable ? "UNBILLED" : "NON_BILLABLE";
        }
        this.updatedAt = Instant.now();
    }

    public void markBilled(UUID invoiceId) {
        if (!"UNBILLED".equals(status))
            throw new IllegalStateException("Only UNBILLED entries can be billed. Current: " + status);
        this.status = "BILLED";
        this.invoiceId = invoiceId;
        this.updatedAt = Instant.now();
    }

    public void writeOff() {
        this.status = "WRITTEN_OFF";
        this.updatedAt = Instant.now();
    }

    @PreUpdate
    void onUpdate() { this.updatedAt = Instant.now(); }
}
