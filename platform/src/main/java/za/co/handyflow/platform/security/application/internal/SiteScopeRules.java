package za.co.handyflow.platform.security.application.internal;

import org.springframework.http.HttpStatus;
import za.co.handyflow.platform.shared.HandyFlowException;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Pure rules for a user's site scope. Scope is per user; a user with no scope sees the whole company. */
public final class SiteScopeRules {

    public static final int MAX_ITEMS = 500;

    private SiteScopeRules() {}

    /** True when a user is limited to some sites. `allowed` is null for "the whole company". */
    public static boolean restricted(Set<UUID> allowed) { return allowed != null; }

    /** True when the user may see this site. A null `allowed` means the whole company. A null site is never allowed. */
    public static boolean allows(Set<UUID> allowed, UUID siteId) {
        if (siteId == null) return false;
        return allowed == null || allowed.contains(siteId);
    }

    /** The answer for a site outside the user's scope is exactly the answer for a site that does not exist. */
    public static HandyFlowException notFound() {
        return new HandyFlowException("Site not found", HttpStatus.NOT_FOUND, "SITE_NOT_FOUND");
    }

    public static void requireSite(Set<UUID> allowed, UUID siteId) {
        if (!allows(allowed, siteId)) throw notFound();
    }

    /**
     * For a list whose site is optional. The whole company may leave the site out. A user limited to some sites must name one,
     * because leaving it out would mean "every site". A site outside the scope is "not found".
     */
    public static void requireChoice(Set<UUID> allowed, UUID siteId) {
        if (allowed == null) return;
        if (siteId == null) {
            throw new HandyFlowException("Choose a site. Your access is limited to some sites.", HttpStatus.BAD_REQUEST, "SITE_REQUIRED");
        }
        requireSite(allowed, siteId);
    }

    /** The answer for a record at a site outside the user's scope: the same plain "not found" a missing record gets. */
    public static HandyFlowException childNotFound() {
        return new HandyFlowException("Not found", HttpStatus.NOT_FOUND, "NOT_FOUND");
    }

    /**
     * For a record that belongs to a site (a post, a camera, an incident ...). `found` says whether the record exists in this company
     * and `siteId` is the site it belongs to (null when it belongs to none). A missing record is left for the service to report;
     * a record at a site the user may not see is "not found". A record with no site is not in any limited user's scope.
     */
    public static void requireChild(Set<UUID> allowed, boolean found, UUID siteId) {
        if (allowed == null || !found) return;
        if (!allows(allowed, siteId)) throw childNotFound();
    }

    public static void requireUnrestricted(Set<UUID> allowed) {
        if (allowed != null) {
            throw new HandyFlowException("This needs access to the whole company. Your access is limited to some sites.", HttpStatus.FORBIDDEN, "SITE_SCOPE_LIMITED");
        }
    }

    /** A person cannot change their own scope: it stops an administrator locking themselves out, or quietly widening their own view. */
    public static void requireNotSelf(UUID actor, UUID target) {
        if (target == null) throw new HandyFlowException("User is required.", HttpStatus.BAD_REQUEST, "USER_REQUIRED");
        if (target.equals(actor)) {
            throw new HandyFlowException("You cannot change your own site access. Ask another administrator.", HttpStatus.CONFLICT, "SCOPE_SELF");
        }
    }

    /** The ids as a list without blanks or repeats, in the order given. Too many is refused. */
    public static List<UUID> clean(List<UUID> raw, String what) {
        Set<UUID> out = new LinkedHashSet<>();
        if (raw != null) for (UUID id : raw) if (id != null) out.add(id);
        if (out.size() > MAX_ITEMS) {
            throw new HandyFlowException("Too many " + what + " (at most " + MAX_ITEMS + ").", HttpStatus.BAD_REQUEST, "SCOPE_TOO_MANY");
        }
        return new ArrayList<>(out);
    }

    /**
     * " AND column IN (?, ?, ...)" for a user limited to some sites, and "" for the whole company. A limited user with no sites matches
     * nothing. `column` is a fixed name written in code, never a request value. Bind the values from {@link #sqlArgs} after the other parameters.
     */
    public static String sqlFilter(String column, Set<UUID> allowed) {
        if (allowed == null) return "";
        if (allowed.isEmpty()) return " AND 1 = 0";
        return " AND " + column + " IN (" + String.join(", ", java.util.Collections.nCopies(allowed.size(), "?")) + ")";
    }

    /** The values for {@link #sqlFilter}: none for the whole company or for an empty scope. */
    public static List<Object> sqlArgs(Set<UUID> allowed) {
        return allowed == null ? new ArrayList<>() : new ArrayList<Object>(allowed);
    }

    /**
     * For a list that takes an optional site: " AND column = ?" when a site was chosen (the controller has already checked it is in the
     * user's scope), otherwise the filter for all the user's sites, or nothing for the whole company. Bind {@link #siteArgs} next.
     */
    public static String siteClause(String column, UUID siteId, Set<UUID> allowed) {
        return siteId != null ? " AND " + column + " = ?" : sqlFilter(column, allowed);
    }

    /** The values for {@link #siteClause}. */
    public static List<Object> siteArgs(UUID siteId, Set<UUID> allowed) {
        if (siteId == null) return sqlArgs(allowed);
        List<Object> one = new ArrayList<>(); one.add(siteId); return one;
    }

    /** Keeps the items whose site the user may see (all of them for a user with no scope). */
    public static <T> List<T> keep(Set<UUID> allowed, List<T> items, java.util.function.Function<T, UUID> siteOf) {
        if (allowed == null) return items;
        List<T> out = new ArrayList<>();
        for (T t : items) if (allows(allowed, siteOf.apply(t))) out.add(t);
        return out;
    }
}
