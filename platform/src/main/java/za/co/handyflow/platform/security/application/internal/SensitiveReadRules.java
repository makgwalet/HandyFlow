// security/application/internal/SensitiveReadRules.java
package za.co.handyflow.platform.security.application.internal;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Which requests are reads of sensitive material that must leave a trace (SP-15), and what the trace says. Pure: it looks only at the
 * HTTP method, the matched route pattern and its path variables. It never sees file names, file contents, bank or ID numbers.
 */
public final class SensitiveReadRules {

    public static final String ACTION_EVIDENCE = "EVIDENCE_DOWNLOADED";
    public static final String ACTION_EXPORT = "EXPORTED";
    public static final String ACTION_PAY_STATEMENT = "PAY_STATEMENT_VIEWED";
    public static final String ACTION_VETTING = "VETTING_VIEWED";
    public static final String ACTION_DECLINED = "DECLINED_REGISTER_VIEWED";
    /** Written by the guard service when someone reveals a full ID or bank account number. */
    public static final String ACTION_PII_REVEALED = "PII_REVEALED";

    /** Everything the access log shows. */
    public static final List<String> LOGGED_ACTIONS = List.of(ACTION_PII_REVEALED, ACTION_EVIDENCE, ACTION_EXPORT, ACTION_PAY_STATEMENT, ACTION_VETTING, ACTION_DECLINED);

    /** The route's subject: entityType/entityId say whose data it is; metadata says which item (ids only). */
    public record Target(String entityType, UUID entityId, String action, Map<String, String> metadata) {}

    private record Route(String pattern, String entityType, String idVar, String action, Map<String, String> fixed, List<String> carry) {}

    private static final String G = "/api/v1/security/guards/{guardId}";

    private static final List<Route> ROUTES = List.of(
            new Route(G + "/screening/{screeningId}/evidence/{evidenceId}/download", "GUARD", "guardId", ACTION_EVIDENCE,
                    Map.of("kind", "SCREENING_EVIDENCE"), List.of("screeningId", "evidenceId")),
            new Route(G + "/competencies/{id}/evidence/{evidenceId}/download", "GUARD", "guardId", ACTION_EVIDENCE,
                    Map.of("kind", "COMPETENCY_EVIDENCE"), List.of("id", "evidenceId")),
            new Route("/api/v1/security/complaints/{id}/evidence/{evidenceId}/download", "COMPLAINT", "id", ACTION_EVIDENCE,
                    Map.of("kind", "COMPLAINT_EVIDENCE"), List.of("evidenceId")),
            new Route("/api/v1/security/incidents/{id}/evidence/{evidenceId}/download", "INCIDENT", "id", ACTION_EVIDENCE,
                    Map.of("kind", "INCIDENT_EVIDENCE"), List.of("evidenceId")),
            new Route("/api/v1/security/payroll/periods/{id}/export/csv", "PAYROLL_PERIOD", "id", ACTION_EXPORT, Map.of("format", "CSV"), List.of()),
            new Route("/api/v1/security/payroll/periods/{id}/export/json", "PAYROLL_PERIOD", "id", ACTION_EXPORT, Map.of("format", "JSON"), List.of()),
            new Route("/api/v1/security/payroll/periods/{id}/guards/{guardId}/pdf", "GUARD", "guardId", ACTION_PAY_STATEMENT, Map.of(), List.of("id")),
            new Route("/api/v1/security/cp/vetting/principals/{principalId}", "PRINCIPAL", "principalId", ACTION_VETTING, Map.of(), List.of()),
            new Route("/api/v1/security/cp/principals/{id}/vetting/pdf", "PRINCIPAL", "id", ACTION_VETTING, Map.of("kind", "COMPLIANCE_PDF"), List.of()),
            new Route("/api/v1/security/cp/vetting/declined", "DECLINED_REGISTER", null, ACTION_DECLINED, Map.of(), List.of())
    );

    /** The route patterns this class watches, so a test can check them against the real controllers. */
    static List<String> patterns() { return ROUTES.stream().map(Route::pattern).toList(); }

    private SensitiveReadRules() {}

    /**
     * @param tenantId the tenant, used as the subject of tenant-wide registers (no single record to point at)
     * @return empty for anything that is not a sensitive read
     */
    public static Optional<Target> classify(String httpMethod, String matchedPattern, Map<String, String> pathVars, UUID tenantId) {
        if (!"GET".equalsIgnoreCase(httpMethod) || matchedPattern == null) return Optional.empty();
        for (Route r : ROUTES) {
            if (!r.pattern().equals(matchedPattern)) continue;
            UUID subject;
            if (r.idVar() == null) {
                if (tenantId == null) return Optional.empty();
                subject = tenantId;
            } else {
                subject = uuid(pathVars, r.idVar());
                if (subject == null) return Optional.empty();
            }
            Map<String, String> meta = new LinkedHashMap<>(r.fixed());
            for (String v : r.carry()) {
                UUID id = uuid(pathVars, v);
                if (id == null) return Optional.empty();
                // the payroll statement's {id} is a period, the competency's {id} a competency: name them for the reader
                meta.put(label(r, v), id.toString());
            }
            return Optional.of(new Target(r.entityType(), subject, r.action(), Map.copyOf(meta)));
        }
        return Optional.empty();
    }

    private static String label(Route r, String var) {
        if (!"id".equals(var)) return var;
        return switch (r.action()) {
            case ACTION_PAY_STATEMENT -> "periodId";
            default -> "competencyId";
        };
    }

    private static UUID uuid(Map<String, String> vars, String name) {
        String v = vars == null ? null : vars.get(name);
        if (v == null) return null;
        try { return UUID.fromString(v); } catch (IllegalArgumentException e) { return null; }
    }

    /** Whether the request's outcome counts as the data having been handed over: a 2xx answer and no exception. */
    public static boolean delivered(int status, Throwable failure) {
        return failure == null && status >= 200 && status < 300;
    }

    /** JSON for the metadata column. Ids and fixed words only, so nothing needs more than quoting. */
    public static String json(Map<String, String> meta, boolean supportSession) {
        StringBuilder b = new StringBuilder("{");
        boolean first = true;
        for (var e : new java.util.TreeMap<>(meta).entrySet()) {
            if (!first) b.append(',');
            first = false;
            b.append('"').append(esc(e.getKey())).append("\":\"").append(esc(e.getValue())).append('"');
        }
        if (supportSession) { if (!first) b.append(','); b.append("\"supportSession\":true"); }
        return b.append('}').toString();
    }

    private static String esc(String s) { return s.replace("\\", "\\\\").replace("\"", "\\\""); }
}
