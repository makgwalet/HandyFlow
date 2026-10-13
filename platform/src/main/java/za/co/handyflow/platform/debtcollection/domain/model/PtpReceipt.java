package za.co.handyflow.platform.debtcollection.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.AggregateRoot;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A payment staff attribute to a promise that invoicing does not (yet) show — e.g. an EFT
 * seen on a bank statement before it is allocated. Append-only: an entry made in error is
 * handled by recording the real position in the case notes and cancelling/re-proposing the PTP
 * if needed, never by editing the receipt.
 * Recording money against the invoice itself stays invoicing's job.
 */
@Entity
@Table(name = "debtcollection_ptp_receipts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PtpReceipt extends AggregateRoot<PtpReceipt> {

    @Column(name = "ptp_id", nullable = false)
    private UUID ptpId;

    @Column(name = "case_id", nullable = false)
    private UUID caseId;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(name = "received_date", nullable = false)
    private LocalDate receivedDate;

    @Column(length = 100)
    private String reference;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "recorded_by_user_id")
    private UUID recordedByUserId;

    public static PtpReceipt of(TenantId tenantId, UUID ptpId, UUID caseId, BigDecimal amount, LocalDate receivedDate,
                                String reference, String notes, UUID recordedBy) {
        if (amount == null || amount.signum() <= 0) throw new IllegalArgumentException("amount must be positive");
        PtpReceipt r = new PtpReceipt();
        r.initTenantId(tenantId);
        r.ptpId = ptpId;
        r.caseId = caseId;
        r.amount = amount;
        r.receivedDate = receivedDate != null ? receivedDate : LocalDate.now();
        r.reference = reference;
        r.notes = notes;
        r.recordedByUserId = recordedBy;
        return r;
    }
}
