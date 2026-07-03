package za.co.handyflow.platform.shared;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

/**
 * Extracts the current authenticated user's id from Spring Security's
 * context.
 * <p>
 * CONFIRMED principal type (from the JwtAuthFilter stack trace seen in
 * production logs 2026-07-03): {@code JwtAuthFilter} sets the principal to
 * a plain {@code String} — presumably the "sub" claim of your JWT, which by
 * convention is the user's id. This class assumes that string parses as a
 * UUID. If that assumption is wrong for your token layout (e.g. it's a
 * username or email instead), the error message below will tell you
 * immediately rather than silently returning a wrong id — fix is to change
 * {@link #extractFromString} to look up the real user id however your JWT
 * actually encodes it (e.g. a separate claim).
 */
public final class UserContext {

    private UserContext() {}

    public static UUID getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new IllegalStateException("No authenticated user in the current security context");
        }
        Object principal = auth.getPrincipal();

        if (principal instanceof String s) {
            return extractFromString(s);
        }
        if (principal instanceof HasUserId hasUserId) {
            return hasUserId.getUserId();
        }
        throw new IllegalStateException(
                "Unrecognized authentication principal type: " + principal.getClass()
                        + " — update za.co.handyflow.platform.shared.UserContext to extract the user id "
                        + "from your actual principal type.");
    }

    private static UUID extractFromString(String principal) {
        try {
            return UUID.fromString(principal);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                    "Authentication principal is a String (\"" + principal + "\") but is not a valid UUID. "
                            + "UserContext assumed your JWT's principal IS the user id — if it's actually a "
                            + "username/email/something else, update extractFromString() in UserContext to "
                            + "resolve the real user id from it (e.g. via a user lookup, or a different JWT claim).");
        }
    }

    public interface HasUserId {
        UUID getUserId();
    }
}