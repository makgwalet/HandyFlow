package za.co.handyflow.platform.security.application.internal;

import java.util.List;
import java.util.regex.Pattern;

/**
 * What a guard may still do when their company's subscription is not in good standing. Pure.
 *
 * <ul>
 *   <li>ACTIVE / PILOT / PAST_DUE / unknown: everything works. An unknown or unreadable status never locks a guard out
 *       (failing closed here would turn a billing lookup error into a safety incident).</li>
 *   <li>SUSPENDED: restricted. No new work is started, but the guard can still raise a panic alarm, close their open
 *       session, return resources, and upload what is already on the phone (scans, incidents, evidence), so nothing is
 *       lost and nobody is left on site without a panic button over an unpaid invoice.</li>
 *   <li>CANCELLED: fully blocked.</li>
 * </ul>
 */
public final class TenantAccessRules {

    public enum Access { FULL, RESTRICTED, BLOCKED }

    private record Allowed(String method, Pattern path) {}

    private static final String UUID_RE = "[0-9a-fA-F-]{36}";

    private static final List<Allowed> RESTRICTED_ALLOWED = List.of(
            new Allowed("POST", Pattern.compile("/api/v1/guard/duress")),
            new Allowed("POST", Pattern.compile("/api/v1/guard/auth/logout")),
            new Allowed("POST", Pattern.compile("/api/v1/guard/sessions/" + UUID_RE + "/close")),
            new Allowed("POST", Pattern.compile("/api/v1/guard/sessions/" + UUID_RE + "/location")),
            new Allowed("POST", Pattern.compile("/api/v1/guard/sessions/resources/" + UUID_RE + "/return")),
            new Allowed("GET",  Pattern.compile("/api/v1/guard/sessions/current")),
            new Allowed("GET",  Pattern.compile("/api/v1/guard/sessions/resources/mine")),
            new Allowed("POST", Pattern.compile("/api/v1/guard/checkpoints/scan")),
            new Allowed("POST", Pattern.compile("/api/v1/guard/incidents")),
            new Allowed("POST", Pattern.compile("/api/v1/guard/incidents/by-ref/[^/]+/evidence")),
            new Allowed("POST", Pattern.compile("/api/v1/guard/incidents/" + UUID_RE + "/evidence")));

    private TenantAccessRules() {}

    public static Access accessFor(String subscriptionStatus) {
        if (subscriptionStatus == null) return Access.FULL;
        return switch (subscriptionStatus.trim().toUpperCase()) {
            case "SUSPENDED" -> Access.RESTRICTED;
            case "CANCELLED" -> Access.BLOCKED;
            default -> Access.FULL;
        };
    }

    public static boolean permits(Access access, String method, String path) {
        return switch (access) {
            case FULL -> true;
            case BLOCKED -> false;
            case RESTRICTED -> {
                String p = path.length() > 1 && path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
                yield RESTRICTED_ALLOWED.stream()
                        .anyMatch(a -> a.method().equalsIgnoreCase(method) && a.path().matcher(p).matches());
            }
        };
    }
}
