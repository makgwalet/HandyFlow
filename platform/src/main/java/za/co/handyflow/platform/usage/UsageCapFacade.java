package za.co.handyflow.platform.usage;

import za.co.handyflow.platform.shared.TenantId;

/**
 * The hard invoice cap. Whether it is enforced is a setting (off until the limits are finally approved), so every caller
 * asks here instead of deciding for itself.
 */
public interface UsageCapFacade {

    /** @param enforced false when the cap is switched off or the business has no tier, so nothing is ever refused */
    record Decision(boolean enforced, boolean allowed, long used, Integer limit, String period) {}

    /**
     * Takes a per-business lock that lasts until the caller's transaction ends, then says whether one more invoice may be
     * issued this month. Call it inside the transaction that issues the invoice, before issuing: two invoices issued at the
     * same moment cannot both take the last place.
     */
    Decision checkAndLock(TenantId tenantId);

    /** As {@link #checkAndLock}, but refuses with 409 INVOICE_CAP_REACHED when the cap does not allow another invoice. */
    void requireCapacity(TenantId tenantId);
}
