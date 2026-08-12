package za.co.handyflow.platform.payrollbureau.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "pay_payments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PayPayment {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "fee_note_id", nullable = false)
    private UUID feeNoteId;

    @Column(name = "amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(name = "paid_date", nullable = false)
    private LocalDate paidDate;

    private String method;
    private String reference;

    @Column(name = "recorded_by_user_id")
    private UUID recordedByUserId;

    @Column(name = "recorded_by_user_name")
    private String recordedByUserName;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static PayPayment create(UUID tenantId, UUID feeNoteId, BigDecimal amount, LocalDate paidDate,
                                    String method, String reference, UUID userId, String userName) {
        PayPayment p = new PayPayment();
        p.tenantId = tenantId;
        p.feeNoteId = feeNoteId;
        p.amount = amount;
        p.paidDate = paidDate;
        p.method = method;
        p.reference = reference;
        p.recordedByUserId = userId;
        p.recordedByUserName = userName;
        p.createdAt = Instant.now();
        return p;
    }
}