package za.co.handyflow.platform.identity.application.internal;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import za.co.handyflow.platform.shared.HandyFlowException;

import java.util.Collection;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * IAM Phase 0.1: nobody can hand out authority they do not hold themselves.
 * Closes audit finding F1 (a holder of only USER_INVITE could invite as ADMIN).
 */
public final class GrantCeiling {

    public static final String CODE = "GRANT_ABOVE_AUTHORITY";

    private GrantCeiling() {}

    /** Authorities of the caller on the current request (empty when unauthenticated). */
    public static Set<String> callerAuthorities() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a == null) return Set.of();
        return a.getAuthorities().stream().map(GrantedAuthority::getAuthority).collect(Collectors.toSet());
    }

    /** Throws 403 {@value #CODE} unless every requested permission is held by the caller. */
    public static void assertWithin(Set<String> callerAuthorities, Collection<String> requested, String what) {
        Set<String> missing = new TreeSet<>(requested);
        missing.removeAll(callerAuthorities);
        if (!missing.isEmpty()) {
            String shown = missing.stream().limit(5).collect(Collectors.joining(", "))
                    + (missing.size() > 5 ? " and " + (missing.size() - 5) + " more" : "");
            throw new HandyFlowException(
                    "You cannot " + what + " because it includes access you do not have yourself (" + shown + ").",
                    HttpStatus.FORBIDDEN, CODE);
        }
    }
}
