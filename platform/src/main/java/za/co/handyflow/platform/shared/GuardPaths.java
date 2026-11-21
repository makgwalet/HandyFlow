package za.co.handyflow.platform.shared;

/**
 * Which requests belong to the guard app. Guard tokens are not tenant-user tokens (different claims, no permissions), so exactly one filter must handle them:
 * GuardJwtFilter takes /api/v1/guard/** and JwtAuthFilter must leave it alone. Both filters use this one definition so they cannot disagree.
 */
public final class GuardPaths {

    public static final String PREFIX = "/api/v1/guard/";

    private GuardPaths() {}

    public static boolean isGuardPath(String requestUri) {
        return requestUri != null && requestUri.startsWith(PREFIX);
    }
}
