package za.co.handyflow.platform.security.application.internal;

import java.time.Duration;
import java.time.Instant;

/** Lifetime and reuse rules for client portal links. Pure. */
final class PortalRules {

    static final int LINK_DAYS = 90;
    static final String PATH_PREFIX = "/site-portal/";

    private PortalRules() {}

    static Instant newExpiry(Instant now) {
        return now.plus(Duration.ofDays(LINK_DAYS));
    }

    /** A link with no expiry recorded is treated as expired: it predates the rule and must be renewed. */
    static boolean expired(Instant expiresAt, Instant now) {
        return expiresAt == null || !expiresAt.isAfter(now);
    }

    /**
     * "Generate" should not silently kill a link the client already has.
     * Keep the current link while it is live, unless a new one was explicitly asked for.
     */
    static boolean keepExisting(boolean enabled, String token, Instant expiresAt, Instant now, boolean regenerate) {
        return !regenerate && enabled && token != null && !expired(expiresAt, now);
    }

    static String path(String token) {
        return PATH_PREFIX + token;
    }
}
