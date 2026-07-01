// security/domain/model/PrincipalVetting.java

package za.co.handyflow.platform.security.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * PrincipalVetting — one due-diligence check on the protected person (client).
 *
 * Part 9.6: the company vets the *client* before accepting an engagement.
 * Checks include sanctions screening, PEP status, adverse media, source of
 * funds, criminal associates — none of which map to GuardScreeningRecord's
 * ScreeningType enum, which is why this is a separate entity rather than
 * reusing that table.
 *
 * A vetting HIT on a principal whose detail is already active escalates to
 * compliance — it doesn't auto-cancel the engagement (that's an operational
 * decision for leadership, not a software constraint).
 */
@Entity
@Table(name = "security_principal_vetting")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class PrincipalVetting {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value",
            column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "principal_id", nullable = false)
    private UUID principalId;

    @Enumerated(EnumType.STRING)
    @Column(name = "vetting_type", nullable = false, length = 30)
    private VettingType vettingType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private VettingResult result = VettingResult.PENDING;

    @Column(name = "conducted_by", length = 200)
    private String conductedBy;

    @Column(name = "conducted_at")
    private LocalDate conductedAt;

    @Column(name = "next_review_at")
    private LocalDate nextReviewAt;

    @Column(name = "report_ref")
    private String reportRef;

    @Column
    private String notes;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // ── Factory ────────────────────────────────────────────────────────────────

    public static PrincipalVetting create(TenantId tenantId, UUID principalId,
                                          VettingType type, UUID createdBy) {
        PrincipalVetting v = new PrincipalVetting();
        v.tenantId         = tenantId;
        v.principalId      = principalId;
        v.vettingType      = type;
        v.result           = VettingResult.PENDING;
        v.createdBy        = createdBy;
        v.createdAt        = Instant.now();
        v.updatedAt        = Instant.now();
        return v;
    }

    // ── Mutations ──────────────────────────────────────────────────────────────

    public void recordResult(VettingResult result, String conductedBy, LocalDate conductedAt,
                             LocalDate nextReviewAt, String reportRef, String notes) {
        this.result        = result;
        this.conductedBy   = conductedBy;
        this.conductedAt   = conductedAt;
        this.nextReviewAt  = nextReviewAt;
        this.reportRef     = reportRef;
        this.notes         = notes;
        this.updatedAt     = Instant.now();
    }

    public boolean isHit()     { return result == VettingResult.HIT; }
    public boolean isPending()  { return result == VettingResult.PENDING; }

    // ── Enums ──────────────────────────────────────────────────────────────────

    public enum VettingType {
        SANCTIONS_SCREENING, PEP_CHECK, ADVERSE_MEDIA,
        SOURCE_OF_FUNDS, CRIMINAL_ASSOCIATES, OTHER
    }

    public enum VettingResult {
        CLEAR, HIT, PENDING, INCONCLUSIVE
    }
}
