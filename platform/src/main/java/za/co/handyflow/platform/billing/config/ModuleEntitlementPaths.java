package za.co.handyflow.platform.billing.config;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * URL prefix to module key for staff-facing APIs whose controllers never call FeatureGuard
 * (IAM entitlement backlog E1). Only prefixes whose module key is in the module catalogue are listed.
 * Public, portal-login and signing-link paths are deliberately absent.
 * Free core (crm, invoicing, catalogue) is not listed until the free-tier model exists. Still to be mapped (key or free/paid decision needed): projects, recruiter, auditor, accountant, pos,
 * desk, creative, marketing, ap, approvals, capture, catalogue, notifications, audit-trail, documents, secure-access.
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
    }

    private ModuleEntitlementPaths() {}

    /** Module key a request path needs, if the path is covered. Matches the prefix itself or anything below it. */
    public static Optional<String> moduleFor(String path) {
        if (path == null) return Optional.empty();
        String p = path.endsWith("/") ? path : path + "/";
        return PREFIX_TO_MODULE.entrySet().stream()
                .filter(e -> p.startsWith(e.getKey()))
                .map(Map.Entry::getValue)
                .findFirst();
    }
}
