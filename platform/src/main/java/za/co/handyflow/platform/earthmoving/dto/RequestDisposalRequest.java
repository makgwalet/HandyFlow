package za.co.handyflow.platform.earthmoving.dto;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

/** Ask to take a machine out of the fleet. Buyer or recipient is required for a sale or transfer. */
public record RequestDisposalRequest(@NotNull UUID assetId, String reason, String details, String counterparty, BigDecimal proceeds) {}
