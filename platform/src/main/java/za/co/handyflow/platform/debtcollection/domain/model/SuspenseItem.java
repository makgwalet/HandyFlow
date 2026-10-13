package za.co.handyflow.platform.debtcollection.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.AggregateRoot;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Something a verified webhook delivered that could not be matched safely to exactly one case — a payment with no
 * recognisable case number, an unsupported currency, a reply from a number that matches no (or several) open cases.
 * It is parked here, visible and pending, instead of being guessed at or dropped. A person resolves it to a case
 * (which then runs the normal ledger / inbound path) or dismisses it with a reason. Idempotent per
 * (kind, externalReference) so provider retries do not create duplicates.
 */
@Entity
@Table(name = "debtcollection_webhook_suspense")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SuspenseItem extends AggregateRoot<SuspenseItem> {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private SuspenseKind kind;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private SuspenseStatus status;

    @Column(nullable = false, length = 500)
    private String reason;

    @Column(name = "external_reference", length = 100)
    private String externalReference;

    @Column(precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(length = 3)
    private String currency;

    @Column(name = "event_date")
    private LocalDate eventDate;

    @Enumerated(EnumType.STRING)
    @Column(length = 15)
    private CommunicationChannel channel;

    /** Payer reference (payments) or sender address/number (messages). */
    @Column(length = 255)
    private String counterparty;

    @Column(columnDefinition = "TEXT")
    private String body;

    @Column(name = "payload_json", columnDefinition = "TEXT")
    private String payloadJson;

    @Column(name = "payment_source", length = 15)
    private String paymentSource;

    @Column(name = "resolved_case_id")
    private UUID resolvedCaseId;

    @Column(name = "resolution_note", length = 500)
    private String resolutionNote;

    @Column(name = "resolved_by")
    private UUID resolvedBy;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    public static SuspenseItem payment(TenantId tenantId, String reason, String reference, BigDecimal amount, String currency,
                                       LocalDate date, String payerReference, String paymentSource, String payloadJson) {
        SuspenseItem s = base(tenantId, SuspenseKind.PAYMENT, reason, reference, payloadJson);
        s.amount = amount;
        s.currency = currency;
        s.eventDate = date;
        s.counterparty = payerReference;
        s.paymentSource = paymentSource;
        return s;
    }

    public static SuspenseItem message(TenantId tenantId, String reason, String providerMessageId, CommunicationChannel channel,
                                       String from, String body, LocalDate date, String payloadJson) {
        SuspenseItem s = base(tenantId, SuspenseKind.MESSAGE, reason, providerMessageId, payloadJson);
        s.channel = channel;
        s.counterparty = from;
        s.body = body;
        s.eventDate = date;
        return s;
    }

    private static SuspenseItem base(TenantId tenantId, SuspenseKind kind, String reason, String ref, String payloadJson) {
        SuspenseItem s = new SuspenseItem();
        s.initTenantId(tenantId);
        s.kind = kind;
        s.status = SuspenseStatus.PENDING;
        s.reason = reason != null && reason.length() > 500 ? reason.substring(0, 500) : reason;
        s.externalReference = ref;
        s.payloadJson = payloadJson;
        return s;
    }

    public void resolve(UUID caseId, String note, UUID by) {
        requirePending();
        if (caseId == null) throw new IllegalArgumentException("caseId is required");
        this.status = SuspenseStatus.RESOLVED;
        this.resolvedCaseId = caseId;
        this.resolutionNote = note;
        this.resolvedBy = by;
        this.resolvedAt = Instant.now();
    }

    public void dismiss(String reason, UUID by) {
        requirePending();
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("A reason is required to dismiss an item");
        this.status = SuspenseStatus.DISMISSED;
        this.resolutionNote = reason.length() > 500 ? reason.substring(0, 500) : reason;
        this.resolvedBy = by;
        this.resolvedAt = Instant.now();
    }

    private void requirePending() {
        if (status != SuspenseStatus.PENDING) throw new IllegalStateException("Item is already " + status);
    }
}
