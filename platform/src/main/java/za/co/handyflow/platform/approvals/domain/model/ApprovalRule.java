package za.co.handyflow.platform.approvals.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * ApprovalRule — the data-driven configuration an ApprovalRequest is
 * matched against at submission time. See ApprovalEngineService for the
 * actual matching/evaluation logic; this entity is pure data.
 * <p>
 * DELIBERATE DEVIATION FROM CONVENTION: tenantId is nullable, a plain
 * UUID column rather than the usual TenantId embeddable (every other
 * entity across this codebase declares tenantId NOT NULL). NULL here
 * means "platform default — applies to any tenant that has no active
 * override of its own for this module+entityType." This is what backs
 * backlog 1.1's Q3 decision (tenant-configurable rules): a tenant who
 * never configures anything still gets a real approval gate — AP's
 * shipped default rule (see the V-migration seed) is exactly this kind
 * of global row — rather than "no rule configured" silently meaning "no
 * approval required at all."
 * <p>
 * conditions/approverChain are JSONB, not typed columns — different
 * modules need genuinely different condition shapes (an amount
 * threshold for AP, a department/branch match for something else), and
 * forcing a common typed structure would defeat the point of a rule
 * table any module can use unmodified. See ApprovalEngineService for
 * the exact conditions/approverChain JSON shape this module evaluates.
 */
@Entity
@Table(name = "approval_rules")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ApprovalRule {

    @Id
    private UUID id = UUID.randomUUID();

    /** Nullable — see class Javadoc. NULL = platform-default, applies to every tenant. */
    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(nullable = false, length = 50) private String module;       // "ap", "creative", ...
    @Column(name = "entity_type", nullable = false, length = 50) private String entityType; // "BILL", "PROOF", ...

    @Column(nullable = false, length = 150) private String name;
    @Column(nullable = false) private boolean active = true;

    /** Lower evaluated first. Rules are matched in priority order, first match wins. */
    @Column(nullable = false) private int priority = 100;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String conditions; // e.g. {"totalAmount": {">=": 10000}} — null/blank = always matches

    @Enumerated(EnumType.STRING)
    @Column(name = "approval_mode", nullable = false, length = 20)
    private ApprovalMode approvalMode;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "approver_chain", columnDefinition = "jsonb", nullable = false)
    private String approverChain; // ordered JSON array — see ApprovalEngineService for the exact shape

    @Column(name = "is_platform_default", nullable = false)
    private boolean platformDefault = false;

    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    public enum ApprovalMode { SEQUENTIAL, PARALLEL_ALL, PARALLEL_ANY_ONE }

    public static ApprovalRule create(UUID tenantId, String module, String entityType, String name,
                                      int priority, String conditions, ApprovalMode mode,
                                      String approverChain, boolean platformDefault) {
        ApprovalRule r = new ApprovalRule();
        r.tenantId = tenantId; // may be null — platform default
        r.module = module;
        r.entityType = entityType;
        r.name = name;
        r.priority = priority;
        r.conditions = conditions;
        r.approvalMode = mode;
        r.approverChain = approverChain;
        r.platformDefault = platformDefault;
        r.createdAt = Instant.now();
        r.updatedAt = Instant.now();
        return r;
    }

    public void update(String name, int priority, String conditions,
                       ApprovalMode mode, String approverChain, boolean active) {
        this.name = name;
        this.priority = priority;
        this.conditions = conditions;
        this.approvalMode = mode;
        this.approverChain = approverChain;
        this.active = active;
        this.updatedAt = Instant.now();
    }

    public void deactivate() {
        this.active = false;
        this.updatedAt = Instant.now();
    }
}