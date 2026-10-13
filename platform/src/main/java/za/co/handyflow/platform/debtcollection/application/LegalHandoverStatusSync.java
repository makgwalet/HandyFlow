package za.co.handyflow.platform.debtcollection.application;

import za.co.handyflow.platform.shared.TenantId;

import java.util.UUID;

/**
 * Callback the legal side uses to report progress on a matter that came from a debt-collection handover.
 * Safe to call for ANY matter: returns false (and does nothing) when the matter did not originate from a handover.
 * Implemented by Debt Collection; call it AFTER your own transaction commits — the implementation runs in its own
 * transaction, so a failure here can never undo the legal-side change.
 */
public interface LegalHandoverStatusSync {

    /** @return true if a transferred handover was linked to this matter and its legal status was updated. */
    boolean syncByMatter(TenantId tenantId, UUID matterId, String legalStatus, String detail);
}
