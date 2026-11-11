package za.co.handyflow.platform.fuel.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/** Confirms a quotation: the tank to supply from, and (only if over the credit limit) why it is allowed. */
public record ConfirmOrderRequest(
        @NotNull UUID tankId,
        @Size(max = 300) String overrideReason
) {}
