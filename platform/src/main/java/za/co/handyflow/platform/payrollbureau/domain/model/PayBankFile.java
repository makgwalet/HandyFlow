package za.co.handyflow.platform.payrollbureau.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** A bank payment file made for a finalised pay run. Its content has bank account numbers in it and is stored encrypted. */
@Entity
@Table(name = "pay_bank_files")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PayBankFile {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "pay_run_id", nullable = false)
    private UUID payRunId;

    @Column(name = "file_number", nullable = false)
    private int fileNumber;

    @Column(name = "status", nullable = false)
    private String status = "ACTIVE"; // ACTIVE | SUPERSEDED

    @Column(name = "payment_count", nullable = false)
    private int paymentCount;

    @Column(name = "total_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "excluded_note")
    private String excludedNote;

    @Column(name = "reason")
    private String reason;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "content", nullable = false)
    @Convert(converter = za.co.handyflow.platform.shared.EncryptedStringConverter.class)
    private String content;

    @Version
    private Long version;

    public static PayBankFile create(UUID tenantId, UUID payRunId, int fileNumber, int paymentCount, BigDecimal totalAmount,
                                     String excludedNote, String reason, UUID createdBy, String content) {
        PayBankFile f = new PayBankFile();
        f.tenantId = tenantId;
        f.payRunId = payRunId;
        f.fileNumber = fileNumber;
        f.paymentCount = paymentCount;
        f.totalAmount = totalAmount;
        f.excludedNote = excludedNote;
        f.reason = reason == null || reason.isBlank() ? null : reason.trim();
        f.createdBy = createdBy;
        f.content = content;
        return f;
    }

    public void supersede() { this.status = "SUPERSEDED"; }
}
