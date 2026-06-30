// security/domain/model/ProtectionDetail.java

package za.co.handyflow.platform.security.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * ProtectionDetail — a close-protection engagement for a principal.
 *
 * This is the CP equivalent of a Site contract — the unit of billing,
 * staffing, and itinerary planning for one period of protection work.
 *
 * Four types:
 *   STATIC  — protection at a single fixed location (residence, office)
 *   MOBILE  — protection while the principal moves through multiple stops
 *   EVENT   — protection for a specific event (gala, conference)
 *   TRAVEL  — protection during travel (airport, hotel, multi-day trip)
 *
 * Lifecycle: PLANNED → ACTIVE → COMPLETED, or → CANCELLED at any point
 * before COMPLETED.
 */
@Entity
@Table(name = "security_protection_details")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class ProtectionDetail {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value",
            column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "principal_id", nullable = false)
    private UUID principalId;

    @Enumerated(EnumType.STRING)
    @Column(name = "detail_type", nullable = false, length = 20)
    private DetailType detailType;

    @Column(name = "start_at", nullable = false)
    private Instant startAt;

    @Column(name = "end_at")
    private Instant endAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DetailStatus status = DetailStatus.PLANNED;

    @Column(name = "billing_rate", precision = 10, scale = 2)
    private BigDecimal billingRate;

    @Column(name = "client_reference", length = 200)
    private String clientReference;

    @Column
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // ── Factory ────────────────────────────────────────────────────────────────

    public static ProtectionDetail create(TenantId tenantId, UUID principalId,
                                          DetailType detailType, Instant startAt,
                                          Instant endAt, BigDecimal billingRate,
                                          String clientReference, String notes) {
        ProtectionDetail d   = new ProtectionDetail();
        d.tenantId           = tenantId;
        d.principalId        = principalId;
        d.detailType         = detailType;
        d.startAt            = startAt;
        d.endAt              = endAt;
        d.status             = DetailStatus.PLANNED;
        d.billingRate        = billingRate;
        d.clientReference    = clientReference;
        d.notes              = notes;
        d.createdAt          = Instant.now();
        d.updatedAt          = Instant.now();
        return d;
    }

    // ── State transitions ──────────────────────────────────────────────────────

    public void activate() {
        if (this.status != DetailStatus.PLANNED) {
            throw new IllegalStateException(
                    "Can only activate a PLANNED detail (current: " + this.status + ")");
        }
        this.status    = DetailStatus.ACTIVE;
        this.updatedAt = Instant.now();
    }

    public void complete() {
        if (this.status != DetailStatus.ACTIVE) {
            throw new IllegalStateException(
                    "Can only complete an ACTIVE detail (current: " + this.status + ")");
        }
        this.status    = DetailStatus.COMPLETED;
        this.updatedAt = Instant.now();
    }

    public void cancel(String reason) {
        if (this.status == DetailStatus.COMPLETED) {
            throw new IllegalStateException("Cannot cancel a COMPLETED detail");
        }
        this.status    = DetailStatus.CANCELLED;
        this.notes     = (this.notes == null || this.notes.isBlank())
                ? reason : this.notes + "\nCancelled: " + reason;
        this.updatedAt = Instant.now();
    }

    public boolean isActive() { return status == DetailStatus.ACTIVE; }

    // ── Enums ──────────────────────────────────────────────────────────────────

    public enum DetailType {
        STATIC, MOBILE, EVENT, TRAVEL
    }

    public enum DetailStatus {
        PLANNED, ACTIVE, COMPLETED, CANCELLED
    }
}
