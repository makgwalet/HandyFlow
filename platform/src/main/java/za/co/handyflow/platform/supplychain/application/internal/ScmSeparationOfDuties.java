package za.co.handyflow.platform.supplychain.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.shared.SeparationOfDutiesChecker;
import za.co.handyflow.platform.shared.TenantId;

import java.util.UUID;

/**
 * Supply Chain's segregation-of-duties rule, on by default.
 * <p>
 * Decision (2026-11-18): the person who did the earlier step does not do the checking step, using the platform rule
 * ({@link SeparationOfDutiesChecker}): refused (409 SAME_PERSON) when someone else holds the checking permission;
 * allowed, and written to the audit trail, when nobody else does, so a one-person company is not locked out and the
 * exception switches itself off the day a second person is given the permission.
 * <p>
 * {@code app.scm.sod.enforce=false} remains as an emergency switch only. It was the default before, which is why the
 * rule never ran.
 */
@Component
@RequiredArgsConstructor
public class ScmSeparationOfDuties {

    private final SeparationOfDutiesChecker checker;

    @Value("${app.scm.sod.enforce:true}")
    private boolean enforce;

    public void require(TenantId tenantId, String checkingPermission, UUID earlier, UUID actor, String message,
                        String itemType, UUID itemId, String what) {
        if (!enforce) return;
        checker.require(tenantId, checkingPermission, earlier, actor, message, itemType, itemId, what);
    }
}
