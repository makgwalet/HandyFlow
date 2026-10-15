package za.co.handyflow.platform.audittrail.application;

import za.co.handyflow.platform.shared.TenantId;

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
}
