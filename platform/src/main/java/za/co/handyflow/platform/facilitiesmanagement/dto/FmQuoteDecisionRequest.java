package za.co.handyflow.platform.facilitiesmanagement.dto;

import jakarta.validation.constraints.Size;

/** {@code note}: optional when accepting, required when declining. */
public record FmQuoteDecisionRequest(@Size(max = 1000) String note) {}
