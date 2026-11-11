package za.co.handyflow.platform.fuel.dto;

import jakarta.validation.constraints.Size;

/** hold = true needs a reason of at least 5 characters (checked in the domain); false releases the hold. */
public record CreditHoldRequest(boolean hold, @Size(max = 300) String reason) {}
