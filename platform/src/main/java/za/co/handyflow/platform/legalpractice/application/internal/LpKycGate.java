package za.co.handyflow.platform.legalpractice.application.internal;

import za.co.handyflow.platform.shared.TenantId;

import java.util.UUID;

/**
 * Asked before the firm takes on a client's matter or receives their money. When the firm has chosen to enforce due diligence, a client
 * whose due diligence is not verified, or is past its review date, is refused with a message saying what to do. When it has not, nothing is refused.
 */
public interface LpKycGate {

    /** @param action what the caller is about to do, in plain words ("open a matter") */
    void requireCleared(TenantId tenantId, UUID clientId, String action);

    LpKycGate NONE = (tenantId, clientId, action) -> { };
}
