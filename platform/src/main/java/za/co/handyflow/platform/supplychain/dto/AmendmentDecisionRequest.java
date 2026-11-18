package za.co.handyflow.platform.supplychain.dto;

import jakarta.validation.constraints.Size;

/** Optional note when approving or withdrawing an amendment; the service requires one when rejecting. */
public record AmendmentDecisionRequest(@Size(max = 500) String note) {}
