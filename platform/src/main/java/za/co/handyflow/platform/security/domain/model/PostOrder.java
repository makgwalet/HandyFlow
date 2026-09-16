package za.co.handyflow.platform.security.domain.model;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

/**
 * Versioned instructions for a site (postId null) or a specific post
 * within that site (postId set) — one entity for both levels, per the
 * product owner's own unified schema. A site-level order carries the
 * things everyone at the site needs (general rules, emergency
 * procedures, evacuation, client rules); a post-level order carries
 * what's specific to that guard's own location (duties, opening/
 * closing, visitor procedures) — both stored in the same shape, since
 * the product owner specified one PostOrder model, not two.
 * <p>
 * Versioning is mandatory, per the product owner's own explicit
 * instruction ("Don't simply overwrite instructions... System creates
 * Post Order v2"): every edit is a NEW row, never an UPDATE to an
 * existing version's own instructions field. publish() is the only way
 * a version becomes the one guards actually see — see that method's
 * own comment for how superseding the previous ACTIVE version works.
 */
@Entity
@Table(name = "security_post_orders")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PostOrder {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "site_id", nullable = false) private UUID siteId;
    @Column(name = "post_id") private UUID postId; // nullable — null means this is the site-level order
    @Column(nullable = false) private int version;
    @Column(nullable = false) private String status = "DRAFT"; // DRAFT | ACTIVE | SUPERSEDED | ARCHIVED

    @Column(name = "effective_from") private Instant effectiveFrom;
    @Column(name = "effective_to") private Instant effectiveTo;

    private String instructions;
    private String duties;
    @Column(name = "emergency_procedures") private String emergencyProcedures;
    @Column(name = "restricted_areas") private String restrictedAreas;
    @Column(name = "access_rules") private String accessRules;

    @Column(name = "created_by") private UUID createdBy;
    @Column(name = "published_by") private UUID publishedBy;
    @Column(name = "published_at") private Instant publishedAt;

    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    public static PostOrder createDraft(TenantId tenantId, UUID siteId, UUID postId, int version,
                                        String instructions, String duties, String emergencyProcedures,
                                        String restrictedAreas, String accessRules, UUID createdBy) {
        PostOrder o = new PostOrder();
        o.tenantId = tenantId;
        o.siteId = siteId;
        o.postId = postId;
        o.version = version;
        o.instructions = instructions;
        o.duties = duties;
        o.emergencyProcedures = emergencyProcedures;
        o.restrictedAreas = restrictedAreas;
        o.accessRules = accessRules;
        o.createdBy = createdBy;
        o.createdAt = Instant.now();
        o.updatedAt = Instant.now();
        return o;
    }

    public void updateDraft(String instructions, String duties, String emergencyProcedures,
                            String restrictedAreas, String accessRules) {
        if (!"DRAFT".equals(status)) {
            throw new IllegalStateException("Only a DRAFT version can be edited in place — publish a new version instead. Current status: " + status);
        }
        this.instructions = instructions;
        this.duties = duties;
        this.emergencyProcedures = emergencyProcedures;
        this.restrictedAreas = restrictedAreas;
        this.accessRules = accessRules;
        this.updatedAt = Instant.now();
    }

    /**
     * Makes this DRAFT the current ACTIVE version. Does NOT itself
     * supersede whatever was previously ACTIVE for this same site/post —
     * that's PostOrderService's own job (it has to find that row first),
     * called via supersede() on the OLD version in the same transaction.
     * Kept as two separate domain calls rather than one method reaching
     * across two entities, matching this codebase's own established
     * single-aggregate-per-call convention.
     */
    public void publish(UUID publishedBy) {
        if (!"DRAFT".equals(status)) {
            throw new IllegalStateException("Only a DRAFT version can be published. Current status: " + status);
        }
        this.status = "ACTIVE";
        this.publishedBy = publishedBy;
        this.publishedAt = Instant.now();
        this.effectiveFrom = Instant.now();
        this.updatedAt = Instant.now();
    }

    /** Called on the previously-ACTIVE version when a new one is published. */
    public void supersede() {
        if (!"ACTIVE".equals(status)) {
            throw new IllegalStateException("Only an ACTIVE version can be superseded. Current status: " + status);
        }
        this.status = "SUPERSEDED";
        this.effectiveTo = Instant.now();
        this.updatedAt = Instant.now();
    }

    public void archive() {
        this.status = "ARCHIVED";
        this.updatedAt = Instant.now();
    }

    public boolean isSiteLevel() { return postId == null; }
}
