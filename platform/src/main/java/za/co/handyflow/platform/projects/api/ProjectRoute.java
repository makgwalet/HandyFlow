package za.co.handyflow.platform.projects.api;

import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Parses a /api/v1/projects/** URL into "which project does this touch, and which
 * kind of data".
 *
 *   /api/v1/projects                              COLLECTION   (list / create)
 *   /api/v1/projects/summary | /time-approvals    SPECIAL      (filtered inside the service)
 *   /api/v1/projects/{projectId}/...              PROJECT_SCOPED
 *   /api/v1/projects/{keyword}/{entityId}/...     ENTITY_SCOPED (tasks, risks, snags, ...)
 *   anything else                                 UNKNOWN      (denied in restricted mode)
 *
 * IDs must be canonical 36-char UUIDs. A looser value (e.g. a leading space that
 * Spring would later trim, or shorthand like "1-1-1-1-1" that UUID.fromString
 * accepts) is UNKNOWN, so the interceptor and the controller can never disagree
 * about which project a URL refers to.
 */
record ProjectRoute(Kind kind, UUID projectId, String entityKeyword, UUID entityId,
                    boolean costDomain, boolean membersDomain) {

    enum Kind { COLLECTION, SPECIAL, PROJECT_SCOPED, ENTITY_SCOPED, UNKNOWN }

    static final String PREFIX = "/api/v1/projects";

    static final Set<String> SPECIAL = Set.of("summary", "time-approvals", "portfolio");

    /** Keep in sync with ProjectScopeResolver's map. */
    static final Set<String> ENTITY_KEYWORDS = Set.of(
            "tasks", "risks", "snags", "site-diaries", "documents", "budget",
            "resources", "change-orders", "rfis", "phases", "time-entries", "issues", "action-items");

    /** Any URL segment in this set marks the route as cost-sensitive. */
    private static final Set<String> COST_SEGMENTS = Set.of("budget", "change-orders");

    private static final Pattern UUID_FORMAT = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    static ProjectRoute parse(String path) {
        if (path == null) return unknown();
        String p = path;
        while (p.length() > 1 && p.endsWith("/")) p = p.substring(0, p.length() - 1);

        if (p.equals(PREFIX)) return new ProjectRoute(Kind.COLLECTION, null, null, null, false, false);
        if (!p.startsWith(PREFIX + "/")) return unknown();

        String[] segs = p.substring(PREFIX.length() + 1).split("/");
        String first = segs[0];

        if (SPECIAL.contains(first)) {
            return segs.length == 1
                    ? new ProjectRoute(Kind.SPECIAL, null, null, null, false, false)
                    : unknown();
        }

        UUID projectId = parseUuid(first);
        if (projectId != null) {
            boolean members = false;
            boolean cost = false;
            for (int i = 1; i < segs.length; i++) {
                if (segs[i].equals("members")) members = true;
                if (COST_SEGMENTS.contains(segs[i])) cost = true;
            }
            return new ProjectRoute(Kind.PROJECT_SCOPED, projectId, null, null, cost, members);
        }

        if (ENTITY_KEYWORDS.contains(first) && segs.length > 1) {
            UUID entityId = parseUuid(segs[1]);
            if (entityId == null) return unknown();   // fail closed — don't let a lenient parser downstream differ
            return new ProjectRoute(Kind.ENTITY_SCOPED, null, first, entityId,
                    COST_SEGMENTS.contains(first), false);
        }
        return unknown();
    }

    private static UUID parseUuid(String s) {
        if (s == null || !UUID_FORMAT.matcher(s).matches()) return null;
        return UUID.fromString(s);
    }

    private static ProjectRoute unknown() {
        return new ProjectRoute(Kind.UNKNOWN, null, null, null, false, false);
    }
}