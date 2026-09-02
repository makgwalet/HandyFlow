package za.co.handyflow.platform.legalpractice.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * An out-of-pocket cost incurred on a matter that gets billed on to the
 * client — Sheriff's fees, correspondent-attorney fees, counsel's fees,
 * deeds-office search fees, courier costs. Same UNBILLED -&gt; BILLED/
 * WRITTEN_OFF lifecycle shape as {@code LpTimeEntry}, without the
 * hours &times; rate calculation (a disbursement's amount is simply what
 * was paid). {@code paidFromTrust} records whether this disbursement was
 * actually settled via an {@code LpTrustTransaction} of type
 * DISBURSEMENT_PAYMENT (the client's own trust money covered it directly)
 * versus the firm paying it from its own funds and recovering it later
 * through billing — both are legitimate; this flag is what tells the two
 * apart on the client's statement.
 */
@Entity
@Table(name = "lp_disbursements")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LpDisbursement {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "matter_id", nullable = false)
    private UUID matterId;

    @Column(name = "disbursement_date", nullable = false)
    private LocalDate disbursementDate;

    @Column(nullable = false)
    private String description;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(name = "paid_from_trust", nullable = false)
    private boolean paidFromTrust = false;

    @Column(nullable = false, length = 20)
    private String status; // UNBILLED | BILLED | WRITTEN_OFF

    @Column(name = "invoice_id")
    private UUID invoiceId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    public static LpDisbursement create(TenantId tenantId, UUID matterId, LocalDate disbursementDate,
                                         String description, BigDecimal amount, boolean paidFromTrust) {
        LpDisbursement d = new LpDisbursement();
        d.tenantId = tenantId;
        d.matterId = matterId;
        d.disbursementDate = disbursementDate != null ? disbursementDate : LocalDate.now();
        d.description = description;
        d.amount = amount;
        d.paidFromTrust = paidFromTrust;
        d.status = "UNBILLED";
        d.createdAt = Instant.now();
        d.updatedAt = Instant.now();
        return d;
    }

    public boolean isEditable() {
        return "UNBILLED".equals(this.status);
    }

    public void update(LocalDate disbursementDate, String description, BigDecimal amount) {
        if (!isEditable()) {
            throw new IllegalStateException("Disbursement is not editable in status " + this.status);
        }
        this.disbursementDate = disbursementDate;
        this.description = description;
        this.amount = amount;
        this.updatedAt = Instant.now();
    }

    public void markBilled(UUID invoiceId) {
        if (!"UNBILLED".equals(this.status)) {
            throw new IllegalStateException("Only an UNBILLED disbursement can be billed, current status: " + this.status);
        }
        this.status = "BILLED";
        this.invoiceId = invoiceId;
        this.updatedAt = Instant.now();
    }

    public void writeOff() {
        if (!"UNBILLED".equals(this.status)) {
            throw new IllegalStateException("Only an UNBILLED disbursement can be written off, current status: " + this.status);
        }
        this.status = "WRITTEN_OFF";
        this.updatedAt = Instant.now();
    }

    @PreUpdate
    void onUpdate() { this.updatedAt = Instant.now(); }
}
