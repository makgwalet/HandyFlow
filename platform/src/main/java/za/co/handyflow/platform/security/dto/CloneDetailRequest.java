// security/dto/CloneDetailRequest.java
package za.co.handyflow.platform.security.dto;

import jakarta.validation.constraints.NotNull;

import java.time.Instant;

/**
 * Spins up a new ProtectionDetail from a previous one for the same
 * principal — the "VC visits campus every semester" case. principalId,
 * detailType, billingRate, and notes are always copied from the source
 * detail; only the fields below are meant to change per occurrence.
 */
public record CloneDetailRequest(
        @NotNull Instant startAt,
        Instant endAt,
        String clientReference
) {}