package za.co.handyflow.platform.security.infrastructure;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * What a public API key may do. Pure rules, no Spring, so they can be tested on their own.
 *
 * Security audit 1.5: keys used to be given USER_READ (and USER_UPDATE for write keys), which no security endpoint accepts, so a key could not read security data,
 * while those authorities opened the identity, tenant and HR employee endpoints. A key with no scope was valid on every path.
 *
 * Now:
 *  - a key gets SECURITY_READ, and SECURITY_MANAGE only when it is not read-only;
 *  - a key can only ever reach /api/v1/security/**, whatever its scope says; no scope means all of that, not all of the platform;
 *  - a key can never reach key and webhook management, payroll, or the guard credential actions (enrol, device replacement, token revocation): a stolen key
 *    must not be able to mint more keys, point webhooks at internal addresses, change pay or take over a guard's login.
 */
public final class ApiKeyAccess {

    public static final String SECURITY_ROOT = "/api/v1/security/";

    /** Shown to the person creating a key and returned to a key that cannot be used. */
    public static final String BRANCH_UNSUPPORTED = "Branch-limited API keys are not supported yet (the security endpoints do not filter by branch). Create the key without a branch.";

    private static final Pattern GUARD_CREDENTIALS = Pattern.compile("^/api/v1/security/guards/[^/]+/(enrol|initiate-device-replacement|revoke-tokens|reveal|face)(/.*)?$");
    private static final List<String> BLOCKED_PREFIXES = List.of(SECURITY_ROOT + "public-api", SECURITY_ROOT + "payroll");

    private ApiKeyAccess() {}

    public static Set<String> authorities(boolean readOnly) {
        return readOnly ? Set.of("SECURITY_READ") : Set.of("SECURITY_READ", "SECURITY_MANAGE");
    }

    /** Paths no API key may call, whatever its scope. */
    public static boolean blocked(String uri) {
        if (uri == null) return true;
        for (String p : BLOCKED_PREFIXES) if (uri.startsWith(p)) return true;
        return GUARD_CREDENTIALS.matcher(uri).matches();
    }

    /** prefixes null or empty means "all security endpoints". Anything outside /api/v1/security/ or on the blocked list is out, even if a prefix names it. */
    public static boolean inScope(String uri, List<String> prefixes) {
        if (uri == null || !uri.startsWith(SECURITY_ROOT) || blocked(uri)) return false;
        if (prefixes == null || prefixes.isEmpty()) return true;
        for (String p : prefixes) if (p != null && !p.isBlank() && uri.startsWith(p)) return true;
        return false;
    }

    /** First problem with a scope someone wants to save, or null. Used when a key is created. */
    public static String scopeProblem(List<String> prefixes) {
        if (prefixes == null) return null;
        for (String p : prefixes) {
            if (p == null || !p.startsWith(SECURITY_ROOT)) return "Every scope prefix must start with " + SECURITY_ROOT + " (API keys only reach the security module)";
        }
        return null;
    }
}
