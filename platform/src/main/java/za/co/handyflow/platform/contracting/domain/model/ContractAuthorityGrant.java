package za.co.handyflow.platform.contracting.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * One piece of authority: a user or a role may do some actions, on some kinds of contract, up to an amount, between two dates.
 * A temporary delegation is a grant that points at the grant it came from; it is only as good as that source.
 */
@Entity
@Table(name = "contract_authority_grants")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ContractAuthorityGrant {
    @Id private UUID id;
    @Column(name = "tenant_id") private UUID tenantId;
    @Column(name = "grantee_user_id") private UUID granteeUserId;
    @Column(name = "grantee_role") private String granteeRole;
    @Column(name = "business_unit") private String businessUnit;
    @Column(name = "contract_types") private String contractTypes = "";
    private String actions;
    @Column(name = "unlimited_amount") private boolean unlimitedAmount;
    @Column(name = "max_amount") private BigDecimal maxAmount;
    @Column(name = "effective_from") private LocalDate effectiveFrom;
    @Column(name = "expires_on") private LocalDate expiresOn;
    private String conditions;
    @Column(name = "delegated_from_id") private UUID delegatedFromId;
    @Column(name = "delegated_by") private UUID delegatedBy;
    private String status = "ACTIVE";
    @Column(name = "revoked_at") private Instant revokedAt;
    @Column(name = "revoked_by") private UUID revokedBy;
    @Column(name = "revoked_reason") private String revokedReason;
    @Column(name = "created_by") private UUID createdBy;
    @Column(name = "created_at") private Instant createdAt;

    public static ContractAuthorityGrant create(UUID tenantId, UUID userId, String role, String businessUnit, Set<String> contractTypes,
                                                Set<AuthorityAction> actions, boolean unlimited, BigDecimal maxAmount, LocalDate from,
                                                LocalDate expires, String conditions, UUID createdBy) {
        ContractAuthorityGrant g = new ContractAuthorityGrant();
        g.id = UUID.randomUUID();
        g.tenantId = tenantId; g.granteeUserId = userId; g.granteeRole = role; g.businessUnit = businessUnit;
        g.contractTypes = String.join(",", new TreeSet<>(contractTypes));
        g.actions = actions.stream().map(Enum::name).sorted().collect(Collectors.joining(","));
        g.unlimitedAmount = unlimited; g.maxAmount = unlimited ? null : maxAmount;
        g.effectiveFrom = from; g.expiresOn = expires; g.conditions = conditions;
        g.createdBy = createdBy; g.createdAt = Instant.now();
        return g;
    }

    /** A temporary delegation: the same shape, tied to the grant it was made from. */
    public static ContractAuthorityGrant delegate(ContractAuthorityGrant source, UUID toUser, Set<String> types, Set<AuthorityAction> actions,
                                                  boolean unlimited, BigDecimal max, LocalDate from, LocalDate expires, String conditions, UUID by) {
        ContractAuthorityGrant g = create(source.tenantId, toUser, null, source.businessUnit, types, actions, unlimited, max, from, expires, conditions, by);
        g.delegatedFromId = source.id;
        g.delegatedBy = by;
        return g;
    }

    public Set<AuthorityAction> actionSet() {
        Set<AuthorityAction> out = EnumSet.noneOf(AuthorityAction.class);
        for (String s : actions.split(",")) if (!s.isBlank()) out.add(AuthorityAction.valueOf(s.trim()));
        return out;
    }

    /** Empty means every contract type. */
    public Set<String> typeSet() {
        Set<String> out = new TreeSet<>();
        for (String s : (contractTypes == null ? "" : contractTypes).split(",")) if (!s.isBlank()) out.add(s.trim());
        return out;
    }

    public boolean isDelegation() { return delegatedFromId != null; }
    public boolean isActive() { return "ACTIVE".equals(status); }

    public void revoke(UUID by, String reason) {
        if (!isActive()) throw new IllegalStateException("This authority was already revoked");
        this.status = "REVOKED"; this.revokedAt = Instant.now(); this.revokedBy = by; this.revokedReason = reason;
    }
}
