package za.co.handyflow.platform.warehousing.dto;

import jakarta.validation.constraints.NotNull;

/** Switches the second-person rule for tariff and minimum-charge changes on or off. A reason is recorded. */
public record TariffApprovalRuleRequest(@NotNull Boolean required, String reason) {}
