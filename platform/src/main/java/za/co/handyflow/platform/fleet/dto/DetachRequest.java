package za.co.handyflow.platform.fleet.dto;

import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.time.Instant;

/** Unhitch an attachment. {@code engineHours} defaults to the machine's current reading and cannot exceed it. */
public record DetachRequest(
        @PositiveOrZero BigDecimal engineHours,
        Instant detachedAt
) {}