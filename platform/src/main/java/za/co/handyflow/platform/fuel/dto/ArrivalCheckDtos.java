package za.co.handyflow.platform.fuel.dto;

import za.co.handyflow.platform.fuel.dto.EligibilityDtos.Finding;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class ArrivalCheckDtos {
    private ArrivalCheckDtos() {}

    /**
     * @param allowed false when anything blocks. Review points do not block here: the driver sees them and the office decides.
     * @param status  MISSING, BLOCKED, FRESH, STALE or RELEASED, decided by the server from its own clock. Null on a view built without a policy.
     * @param freshHours the company's window; expiresAt is when the check (or its release) stops counting.
     */
    public record ArrivalCheckView(UUID deliveryId, boolean allowed, List<Finding> findings, Instant checkedAt,
                                   UUID checkId, String status, Integer freshHours, Instant expiresAt, Instant releasedAt) {
        public ArrivalCheckView(UUID deliveryId, boolean allowed, List<Finding> findings, Instant checkedAt) {
            this(deliveryId, allowed, findings, checkedAt, null, null, null, null, null);
        }
    }

    /** The company's arrival check rules. enforced: whether the server refuses protected steps without a clear check. */
    public record ArrivalPolicy(int freshHours, int defaultFreshHours, boolean enforced) {}

    public record ArrivalPolicyRequest(@jakarta.validation.constraints.Min(1) @jakarta.validation.constraints.Max(72) Integer freshHours, boolean enforced) {}

    public record ReleaseRequest(@jakarta.validation.constraints.NotBlank String reasonCode,
                                 @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(min = 10, max = 1000) String explanation) {}
}
