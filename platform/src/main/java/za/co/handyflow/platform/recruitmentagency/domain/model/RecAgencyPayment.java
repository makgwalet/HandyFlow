package za.co.handyflow.platform.recruitmentagency.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Same shape as payrollbureau.PayPayment — proven pattern, reused directly. */
@Entity
@Table(name = "reca_payments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RecAgencyPayment {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "invoice_id", nullable = false)
    private UUID invoiceId;

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

    public static RecAgencyPayment create(UUID tenantId, UUID invoiceId, BigDecimal amount, LocalDate paidDate,
                                          String method, String reference, UUID userId, String userName) {
        RecAgencyPayment p = new RecAgencyPayment();
        p.tenantId = tenantId;
        p.invoiceId = invoiceId;
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