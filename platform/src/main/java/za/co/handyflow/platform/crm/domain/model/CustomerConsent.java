package za.co.handyflow.platform.crm.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

/**
 * CustomerConsent — POPIA Section 11 consent record.
 *
 * Persists even after the customer is soft-deleted, because you need to
 * prove lawful processing for the entire period data was held.
 *
 * WHY @Entity here but not for Booking or Invoice?
 * CustomerConsent lives inside the CRM module boundary — it's about the
 * customer's relationship with their data, not about a booking or invoice.
 * CRM owns it.  Cross-module reads (bookings, invoices) use native SQL to
 * avoid entity coupling.  Consent is CRM's own concept.
 */
@Entity
@Table(name = "customer_consent")
@Getter
public class CustomerConsent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    /**
     * The POPIA Section 11 lawful basis for processing.
     * Most HandyFlow customers will be CONSENT (actively opted in)
     * or CONTRACT (processing necessary to deliver the service).
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "lawful_basis", nullable = false, length = 50)
    private LawfulBasis lawfulBasis = LawfulBasis.CONSENT;

    /**
     * What processing activities this consent covers.
     * Stored as a Postgres TEXT[] — e.g. {"SERVICE_DELIVERY", "MARKETING", "ANALYTICS"}
     */
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "purposes", columnDefinition = "text[]")
    private String[] purposes = {};

    @Column(name = "consented_at", nullable = false)
    private Instant consentedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "consent_source", nullable = false, length = 50)
    private ConsentSource consentSource;

    @Column(name = "consent_evidence")
    private String consentEvidence;

    @Column(name = "withdrawn_at")
    private Instant withdrawnAt;

    @Column(name = "withdrawal_reason")
    private String withdrawalReason;

    /**
     * Retention expiry: when this customer's data should be reviewed/purged.
     * Set by CustomerRetentionScheduler.
     * Null = no retention date set yet.
     */
    @Column(name = "retention_expires_at")
    private Instant retentionExpiresAt;

    @Column(name = "last_reviewed_at")
    private Instant lastReviewedAt;

    @Column(name = "reviewed_by")
    private UUID reviewedBy;

    /**
     * FIX: "no consent-expiring-soon reminder" gap — the retention
     * scheduler only fired after expiry; nothing warned proactively before
     * it lapsed. Tracks whether the 30-days-before-expiry reminder has
     * already gone out for this consent record, so
     * CustomerRetentionScheduler.sendExpiryRemindersForTenant() fires
     * exactly once per record — not every night for the entire 30-day
     * window it's inside — same edge-triggered pattern used for low-stock
     * and low-balance alerts elsewhere in this codebase.
     */
    @Column(name = "expiry_reminder_sent_at")
    private Instant expiryReminderSentAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    // ── Factory ───────────────────────────────────────────────────────────────

    public static CustomerConsent create(TenantId tenantId,
                                         UUID customerId,
                                         LawfulBasis lawfulBasis,
                                         String[] purposes,
                                         ConsentSource source,
                                         String evidence) {
        var c = new CustomerConsent();
        c.tenantId      = tenantId;
        c.customerId    = customerId;
        c.lawfulBasis   = lawfulBasis;
        c.purposes      = purposes;
        c.consentedAt   = Instant.now();
        c.consentSource = source;
        c.consentEvidence = evidence;
        c.createdAt     = Instant.now();
        c.updatedAt     = Instant.now();
        return c;
    }

    // ── Domain methods ────────────────────────────────────────────────────────

    public void withdraw(String reason) {
        this.withdrawnAt      = Instant.now();
        this.withdrawalReason = reason;
        this.updatedAt        = Instant.now();
    }

    public void setRetentionExpiry(Instant expiresAt) {
        this.retentionExpiresAt = expiresAt;
        this.updatedAt          = Instant.now();
    }

    public void recordReview(UUID reviewedByUserId) {
        this.lastReviewedAt = Instant.now();
        this.reviewedBy     = reviewedByUserId;
        this.updatedAt      = Instant.now();
    }

    public void markExpiryReminderSent() {
        this.expiryReminderSentAt = Instant.now();
        this.updatedAt            = Instant.now();
    }

    public boolean isActive()   { return withdrawnAt == null; }
    public boolean isExpired()  { return retentionExpiresAt != null && Instant.now().isAfter(retentionExpiresAt); }

    // ── Enums ─────────────────────────────────────────────────────────────────

    public enum LawfulBasis {
        CONSENT,            // Customer explicitly opted in
        CONTRACT,           // Processing necessary to deliver the service
        LEGAL_OBLIGATION,   // Required by law (e.g. SARS record-keeping)
        VITAL_INTEREST,     // Emergency/life-safety processing
        PUBLIC_INTEREST,    // Rare — specific statutory functions
        LEGITIMATE_INTEREST // Business interest outweighs privacy concern
    }

    public enum ConsentSource {
        WEB_FORM,    // Online contact/signup form
        IMPORT,      // Bulk import (legacy data)
        PHONE,       // Verbal consent over phone (evidence = call reference)
        IN_PERSON,   // Physical signed document
        EMAIL        // Email opt-in confirmation
    }

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = Instant.now();
        if (updatedAt == null) updatedAt = Instant.now();
    }

    @PreUpdate
    void preUpdate() { updatedAt = Instant.now(); }
}