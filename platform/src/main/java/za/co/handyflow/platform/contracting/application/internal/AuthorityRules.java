package za.co.handyflow.platform.contracting.application.internal;

import za.co.handyflow.platform.contracting.domain.model.AuthorityAction;
import za.co.handyflow.platform.contracting.domain.model.ContractAuthorityGrant;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

/**
 * The rules of delegated authority, with no database and no clock of their own so they can be tested exactly.
 *
 * <ul>
 *   <li>A grant counts only while it is active, inside its dates and, for a delegation, while the grant it came from still counts.</li>
 *   <li>It must name the action, cover the contract type (an empty list covers every type) and the amount.</li>
 *   <li>Limits are in rand. A contract in another currency is only covered by a grant with no financial limit.</li>
 *   <li>A delegation can never carry more than its source: not more actions, types, value or time.</li>
 * </ul>
 */
public final class AuthorityRules {
    private AuthorityRules() {}

    public record Context(String contractType, BigDecimal amount, String currency) {}
    public record Decision(boolean allowed, String reason, UUID grantId) {
        static Decision yes(UUID grantId) { return new Decision(true, null, grantId); }
        static Decision no(String why) { return new Decision(false, why, null); }
    }

    /** Whether the grant is in force on this day. */
    public static boolean inForce(ContractAuthorityGrant g, Map<UUID, ContractAuthorityGrant> all, LocalDate today) {
        if (!g.isActive() || today.isBefore(g.getEffectiveFrom())) return false;
        if (g.getExpiresOn() != null && today.isAfter(g.getExpiresOn())) return false;
        if (g.isDelegation()) {
            ContractAuthorityGrant src = all.get(g.getDelegatedFromId());
            return src != null && !src.isDelegation() && inForce(src, all, today);
        }
        return true;
    }

    public static boolean appliesTo(ContractAuthorityGrant g, UUID userId, Collection<String> roles) {
        if (g.getGranteeUserId() != null) return g.getGranteeUserId().equals(userId);
        return g.getGranteeRole() != null && roles != null && roles.contains(g.getGranteeRole());
    }

    public static Decision evaluate(Collection<ContractAuthorityGrant> grants, UUID userId, Collection<String> roles,
                                    AuthorityAction action, Context ctx, LocalDate today) {
        Map<UUID, ContractAuthorityGrant> byId = new HashMap<>();
        for (ContractAuthorityGrant g : grants) byId.put(g.getId(), g);
        List<ContractAuthorityGrant> mine = grants.stream().filter(g -> appliesTo(g, userId, roles)).toList();
        if (mine.isEmpty()) return Decision.no("You have no delegated contract authority.");
        List<ContractAuthorityGrant> live = mine.stream().filter(g -> inForce(g, byId, today)).toList();
        if (live.isEmpty()) return Decision.no("Your contract authority has expired or been revoked.");
        List<ContractAuthorityGrant> forAction = live.stream().filter(g -> g.actionSet().contains(action)).toList();
        if (forAction.isEmpty()) return Decision.no("Your authority does not include " + action.name().toLowerCase() + ".");
        List<ContractAuthorityGrant> forType = forAction.stream()
                .filter(g -> g.typeSet().isEmpty() || (ctx.contractType() != null && g.typeSet().contains(ctx.contractType()))).toList();
        if (forType.isEmpty()) return Decision.no("Your authority does not cover this type of contract.");
        boolean foreign = ctx.currency() != null && !ctx.currency().isBlank() && !"ZAR".equalsIgnoreCase(ctx.currency());
        BigDecimal amount = ctx.amount() == null ? BigDecimal.ZERO : ctx.amount();
        for (ContractAuthorityGrant g : forType) {
            if (g.isUnlimitedAmount()) return Decision.yes(g.getId());
            if (!foreign && amount.compareTo(g.getMaxAmount()) <= 0) return Decision.yes(g.getId());
        }
        if (foreign) return Decision.no("Your authority limits are in rand. A contract in " + ctx.currency() + " needs authority with no financial limit.");
        BigDecimal best = forType.stream().map(ContractAuthorityGrant::getMaxAmount).max(Comparator.naturalOrder()).orElse(BigDecimal.ZERO);
        return Decision.no("This contract is worth more than your limit of R" + best.stripTrailingZeros().toPlainString() + ".");
    }

    /**
     * Checks a delegation request against the grant it is made from. Returns null when it is allowed, otherwise what is wrong.
     * {@code types} empty means "every type".
     */
    public static String checkDelegation(ContractAuthorityGrant source, Map<UUID, ContractAuthorityGrant> all, UUID delegator, Collection<String> delegatorRoles,
                                         UUID toUser, Set<AuthorityAction> actions, Set<String> types, boolean unlimited, BigDecimal max,
                                         LocalDate from, LocalDate expires, LocalDate today) {
        if (source.isDelegation()) return "A delegation cannot be delegated again.";
        if (!appliesTo(source, delegator, delegatorRoles)) return "You can only delegate authority you hold yourself.";
        if (!inForce(source, all, today)) return "That authority is not in force, so it cannot be delegated.";
        if (toUser == null) return "Choose who the authority is delegated to.";
        if (toUser.equals(delegator)) return "You cannot delegate authority to yourself.";
        if (actions == null || actions.isEmpty()) return "Choose at least one action to delegate.";
        if (!source.actionSet().containsAll(actions)) return "You cannot delegate an action you are not authorised to do.";
        if (!source.typeSet().isEmpty()) {
            if (types == null || types.isEmpty()) return "Your authority covers only some contract types, so name the types you are delegating.";
            if (!source.typeSet().containsAll(types)) return "You cannot delegate a contract type you are not authorised for.";
        }
        if (!source.isUnlimitedAmount()) {
            if (unlimited) return "You cannot delegate more than your own financial limit.";
            if (max == null || max.signum() < 0) return "Give the most the delegate may authorise.";
            if (max.compareTo(source.getMaxAmount()) > 0) return "You cannot delegate more than your own limit of R" + source.getMaxAmount().stripTrailingZeros().toPlainString() + ".";
        } else if (!unlimited && (max == null || max.signum() < 0)) {
            return "Give the most the delegate may authorise, or choose no limit.";
        }
        if (from == null || expires == null) return "A delegation needs a start date and an end date.";
        if (expires.isBefore(from)) return "The end date cannot be before the start date.";
        if (expires.isBefore(today)) return "The end date has already passed.";
        if (from.isBefore(source.getEffectiveFrom())) return "A delegation cannot start before your own authority does.";
        if (source.getExpiresOn() != null && expires.isAfter(source.getExpiresOn()))
            return "A delegation cannot outlast your own authority, which ends on " + source.getExpiresOn() + ".";
        return null;
    }
}
