package za.co.handyflow.platform.events.dto;

import jakarta.validation.constraints.Size;

/** Optional body. overrideReason is only needed when approving would take the budget line over its planned amount. */
public record ApproveCommitmentRequest(
        @Size(max = 500) String overrideReason
) {}