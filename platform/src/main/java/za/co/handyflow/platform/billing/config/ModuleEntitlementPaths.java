package za.co.handyflow.platform.billing.config;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * URL prefix to module key for staff-facing APIs whose controllers never call FeatureGuard
 * (IAM entitlement backlog E1). Only prefixes whose module key is in the module catalogue are listed.
 * Public, portal-login and signing-link paths are deliberately absent.
 * Free core (crm, invoicing, catalogue) is not listed: it is derived from any paid module (docs/commercial).
 *
 * Three groups:
 *  - sellable: the key has a module_catalogue row, so the check can be enforced;
 *  - report-only ({@link #REPORT_ONLY}): the module is built but has no catalogue row (projects, auditor, accountant,
 *    ap), so enforcing would lock out every tenant. These are logged but never refused until a row exists;
 *  - deliberately unmapped: platform services shared by all modules (approvals, capture, notifications, audit-trail,
 *    documents, secure-access) and external portals, pending decision D-05.
 * Supply chain is mapped to the catalogue spelling "supply_chain", the same key Agriculture's supplier endpoints now
 * guard (they used "supplychain", which no tenant holds; gap G-02, fixed).
 */
public final class ModuleEntitlementPaths {

    private static final Map<String, String> PREFIX_TO_MODULE = new LinkedHashMap<>();
    static {
        PREFIX_TO_MODULE.put("/api/v1/hr/", "hr");
        PREFIX_TO_MODULE.put("/api/v1/accounting/", "accounting");
        PREFIX_TO_MODULE.put("/api/v1/expenses/", "expenses");
        PREFIX_TO_MODULE.put("/api/v1/contracts/", "contracting");
        PREFIX_TO_MODULE.put("/api/v1/bookings/", "bookings");
        PREFIX_TO_MODULE.put("/api/v1/events/", "events");
        PREFIX_TO_MODULE.put("/api/v1/fleet/telematics/", "fleet");
        PREFIX_TO_MODULE.put("/api/v1/security/", "security");
        PREFIX_TO_MODULE.put("/api/v1/guard/", "security");
        // catalogue rows exist
        PREFIX_TO_MODULE.put("/api/v1/creative/", "creative");
        PREFIX_TO_MODULE.put("/api/v1/desk/", "desk");
        PREFIX_TO_MODULE.put("/api/v1/recruiter/", "recruiter");
        PREFIX_TO_MODULE.put("/api/v1/pos/", "pos");
        PREFIX_TO_MODULE.put("/api/v1/tasks/", "tasks");
        PREFIX_TO_MODULE.put("/api/v1/marketing/", "marketing");
        PREFIX_TO_MODULE.put("/api/v1/supply-chain/", "supply_chain");
        // no catalogue row yet: report only (see REPORT_ONLY)
        PREFIX_TO_MODULE.put("/api/v1/projects/", "projects");
        PREFIX_TO_MODULE.put("/api/v1/auditors/", "auditor");
        PREFIX_TO_MODULE.put("/api/v1/accountant/", "accountant");
        PREFIX_TO_MODULE.put("/api/v1/ap/", "ap");
    }

    /** Excluded before the prefix map is consulted: external portals served under a covered prefix. */
    private static final java.util.List<String> EXCLUDED_PREFIXES = java.util.List.of("/api/v1/accountant/portal/");

    /** Built modules with no module_catalogue row: reported, never enforced. */
    public static final Set<String> REPORT_ONLY = Set.of("projects", "auditor", "accountant", "ap");

    /** True when a refusal may be enforced for this key. */
    public static boolean enforceable(String moduleKey) {
        return moduleKey != null && !REPORT_ONLY.contains(moduleKey);
    }

    private ModuleEntitlementPaths() {}

    /** Module key a request path needs, if the path is covered. Matches the prefix itself or anything below it. */
    public static Optional<String> moduleFor(String path) {
        if (path == null) return Optional.empty();
        String p = path.endsWith("/") ? path : path + "/";
        if (EXCLUDED_PREFIXES.stream().anyMatch(p::startsWith)) return Optional.empty();
        return PREFIX_TO_MODULE.entrySet().stream()
                .filter(e -> p.startsWith(e.getKey()))
                .map(Map.Entry::getValue)
                .findFirst();
    }
}
