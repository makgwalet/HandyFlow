package za.co.handyflow.platform.contracting.dto;

import za.co.handyflow.platform.contracting.domain.model.AuthorityAction;
import za.co.handyflow.platform.contracting.domain.model.ContractAuthorityEvent;
import za.co.handyflow.platform.contracting.domain.model.ContractAuthorityGrant;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class ContractAuthorityDtos {
    private ContractAuthorityDtos() {}

    public record SettingsView(boolean enforced) {}
    public record SettingsRequest(boolean enforced) {}

    public record GrantRequest(UUID userId, String role, String businessUnit, Set<String> contractTypes, Set<AuthorityAction> actions,
                               boolean unlimited, BigDecimal maxAmount, LocalDate effectiveFrom, LocalDate expiresOn, String conditions) {}
    public record DelegateRequest(UUID toUserId, Set<AuthorityAction> actions, Set<String> contractTypes, boolean unlimited, BigDecimal maxAmount,
                                  LocalDate effectiveFrom, LocalDate expiresOn, String conditions) {}
    public record RevokeRequest(String reason) {}

    public record GrantView(UUID id, UUID userId, String role, String businessUnit, List<String> contractTypes, List<AuthorityAction> actions,
                            boolean unlimited, BigDecimal maxAmount, LocalDate effectiveFrom, LocalDate expiresOn, String conditions,
                            UUID delegatedFromId, UUID delegatedBy, String status, boolean inForce, boolean mine, Instant revokedAt, String revokedReason, Instant createdAt) {
        public static GrantView of(ContractAuthorityGrant g, boolean inForce, boolean mine) {
            return new GrantView(g.getId(), g.getGranteeUserId(), g.getGranteeRole(), g.getBusinessUnit(), List.copyOf(g.typeSet()),
                    List.copyOf(g.actionSet()), g.isUnlimitedAmount(), g.getMaxAmount(), g.getEffectiveFrom(), g.getExpiresOn(), g.getConditions(),
                    g.getDelegatedFromId(), g.getDelegatedBy(), g.getStatus(), inForce, mine, g.getRevokedAt(), g.getRevokedReason(), g.getCreatedAt());
        }
    }

    public record EventView(UUID id, UUID grantId, String eventType, UUID actorUserId, String detail, Instant createdAt) {
        public static EventView of(ContractAuthorityEvent e) {
            return new EventView(e.getId(), e.getGrantId(), e.getEventType(), e.getActorUserId(), e.getDetail(), e.getCreatedAt());
        }
    }

    public record CheckView(boolean allowed, String reason, boolean enforced) {}
}
