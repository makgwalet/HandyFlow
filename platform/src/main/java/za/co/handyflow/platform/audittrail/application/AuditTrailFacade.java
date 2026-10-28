package za.co.handyflow.platform.audittrail.application;

import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Record an audit action. The write joins the caller's transaction, so an action that fails to be recorded does not happen. */
public interface AuditTrailFacade {

    String STAFF = "STAFF";
    String AUDITOR = "AUDITOR";

    /** Recorded as the signed-in staff member (taken from the request context). before/after may be null. */
    void record(TenantId tenantId, String action, String itemType, UUID itemId, Map<String, ?> before, Map<String, ?> after);

    /** Recorded as someone who is not the signed-in staff member, such as an external auditor in the portal. */
    void recordAs(TenantId tenantId, String actorType, UUID actorId, String actorName,
                  String action, String itemType, UUID itemId, Map<String, ?> before, Map<String, ?> after);

    /** One recorded action, as a feed shows it. */
    record Activity(String actorName, String action, String itemType, UUID itemId, Instant at) {}

    /** The newest actions whose code matches any of the SQL LIKE patterns (such as "FINDING_%"), newest first, at most `limit` (capped at 100). */
    List<Activity> recent(TenantId tenantId, List<String> actionPatterns, int limit);
}
