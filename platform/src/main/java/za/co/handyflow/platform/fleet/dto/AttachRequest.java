package za.co.handyflow.platform.fleet.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Hitch an attachment to a machine. {@code engineHours} defaults to the machine's current reading and cannot exceed it. */
public record AttachRequest(
        @NotNull UUID vehicleId,
        @PositiveOrZero BigDecimal engineHours,
        Instant attachedAt
) {}