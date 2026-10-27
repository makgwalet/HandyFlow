package za.co.handyflow.platform.facilitiesmanagement.domain.model;

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
 * A priced offer to a client for work at one of its sites. DRAFT (staff editing) -> SENT (visible to the client in the
 * portal, frozen) -> ACCEPTED or DECLINED by the client, or EXPIRED when {@code validUntil} passes, or WITHDRAWN by staff.
 * Only a SENT quote that is still within its validity can be accepted or declined. Amounts are VAT-exclusive lines plus
 * VAT at the rate that applied when the quote was last prepared.
 */
@Entity
@Table(name = "fm_quotes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FmQuote {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "quote_number", nullable = false, length = 30)
    private String quoteNumber;

    @Column(name = "client_id", nullable = false)
    private UUID clientId;
    @Column(name = "site_id", nullable = false)
    private UUID siteId;
    @Column(name = "asset_id")
    private UUID assetId;

    /** The request this quote prices, or the work order created when the client accepted. */
    @Column(name = "work_order_id")
    private UUID workOrderId;

    @Column(nullable = false, length = 200)
    private String title;
    @Column(length = 2000)
    private String description;

    @Column(nullable = false, length = 20)
    private String status = "DRAFT";

    @Column(name = "valid_until", nullable = false)
    private LocalDate validUntil;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal subtotal = BigDecimal.ZERO;
    @Column(name = "vat_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal vatRate = BigDecimal.ZERO;
    @Column(name = "vat_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal vatAmount = BigDecimal.ZERO;
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal total = BigDecimal.ZERO;

    /** Never shown to the client. */
    @Column(name = "internal_note", length = 1000)
    private String internalNote;

    @Column(name = "sent_at")
    private Instant sentAt;
    @Column(name = "sent_by_user_id")
    private UUID sentByUserId;

    @Column(name = "decided_at")
    private Instant decidedAt;
    @Column(name = "decided_by_portal_user_id")
    private UUID decidedByPortalUserId;
    /** The client's reason (declining) or comment (accepting); for WITHDRAWN, staff's reason. */
    @Column(name = "decision_note", length = 1000)
    private String decisionNote;

    @Column(name = "created_by_user_id")
    private UUID createdByUserId;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    public static FmQuote create(TenantId tenantId, String quoteNumber, UUID clientId, UUID siteId, UUID assetId, UUID workOrderId,
                                 String title, String description, LocalDate validUntil, String internalNote, UUID createdBy) {
        FmQuote q = new FmQuote();
        q.tenantId = tenantId;
        q.quoteNumber = quoteNumber;
        q.clientId = clientId;
        q.siteId = siteId;
        q.assetId = assetId;
        q.workOrderId = workOrderId;
        q.createdByUserId = createdBy;
        q.createdAt = Instant.now();
        q.updatedAt = q.createdAt;
        q.setContent(title, description, validUntil, internalNote);
        return q;
    }

    /** Editing is only possible while DRAFT; client, site and asset are fixed at creation. */
    public void edit(String title, String description, LocalDate validUntil, String internalNote) {
        requireStatus("DRAFT", "Only a draft quote can be changed");
        setContent(title, description, validUntil, internalNote);
        this.updatedAt = Instant.now();
    }

    private void setContent(String title, String description, LocalDate validUntil, String internalNote) {
        if (title == null || title.isBlank()) throw new IllegalArgumentException("A title is required");
        if (title.trim().length() > 200) throw new IllegalArgumentException("The title can be at most 200 characters");
        if (description != null && description.trim().length() > 2000) throw new IllegalArgumentException("The description can be at most 2000 characters");
        if (validUntil == null) throw new IllegalArgumentException("A valid-until date is required");
        if (internalNote != null && internalNote.trim().length() > 1000) throw new IllegalArgumentException("The internal note can be at most 1000 characters");
        this.title = title.trim();
        this.description = description == null || description.isBlank() ? null : description.trim();
        this.validUntil = validUntil;
        this.internalNote = internalNote == null || internalNote.isBlank() ? null : internalNote.trim();
    }

    public void setAmounts(BigDecimal subtotal, BigDecimal vatRate, BigDecimal vatAmount, BigDecimal total) {
        this.subtotal = subtotal;
        this.vatRate = vatRate;
        this.vatAmount = vatAmount;
        this.total = total;
        this.updatedAt = Instant.now();
    }

    public void send(UUID userId, LocalDate today) {
        requireStatus("DRAFT", "Only a draft quote can be sent");
        if (validUntil.isBefore(today)) throw new IllegalArgumentException("The valid-until date has already passed");
        if (total.signum() <= 0) throw new IllegalArgumentException("A quote needs a total above zero");
        this.status = "SENT";
        this.sentAt = Instant.now();
        this.sentByUserId = userId;
        this.updatedAt = this.sentAt;
    }

    public boolean isExpiredOn(LocalDate today) { return "SENT".equals(status) && validUntil.isBefore(today); }

    /** The client may decide only while SENT and inside the validity period. */
    public boolean isOpenForDecision(LocalDate today) { return "SENT".equals(status) && !validUntil.isBefore(today); }

    public void accept(UUID portalUserId, String note, LocalDate today) {
        requireOpen(today);
        this.status = "ACCEPTED";
        decide(portalUserId, note);
    }

    public void decline(UUID portalUserId, String reason, LocalDate today) {
        requireOpen(today);
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("Please say why the quote is being declined");
        this.status = "DECLINED";
        decide(portalUserId, reason);
    }

    public void withdraw(String reason) {
        requireStatus("SENT", "Only a quote that has been sent and not yet answered can be withdrawn");
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("A reason is required to withdraw a quote");
        this.status = "WITHDRAWN";
        this.decisionNote = reason.trim();
        this.decidedAt = Instant.now();
        this.updatedAt = this.decidedAt;
    }

    /** Daily sweep: a SENT quote past its validity becomes EXPIRED. */
    public void expire(LocalDate today) {
        if (!isExpiredOn(today)) throw new IllegalStateException("This quote has not expired");
        this.status = "EXPIRED";
        this.updatedAt = Instant.now();
    }

    public void linkWorkOrder(UUID workOrderId) {
        this.workOrderId = workOrderId;
        this.updatedAt = Instant.now();
    }

    private void decide(UUID portalUserId, String note) {
        this.decidedByPortalUserId = portalUserId;
        this.decisionNote = note == null || note.isBlank() ? null : (note.trim().length() > 1000 ? note.trim().substring(0, 1000) : note.trim());
        this.decidedAt = Instant.now();
        this.updatedAt = this.decidedAt;
    }

    private void requireOpen(LocalDate today) {
        if (isExpiredOn(today)) throw new IllegalStateException("This quote expired on " + validUntil);
        requireStatus("SENT", "This quote is " + status + " and can no longer be answered");
    }

    private void requireStatus(String expected, String message) {
        if (!expected.equals(status)) throw new IllegalStateException(message);
    }

    @PreUpdate
    void onUpdate() { this.updatedAt = Instant.now(); }
}
