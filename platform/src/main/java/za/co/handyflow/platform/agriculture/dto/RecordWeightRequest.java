package za.co.handyflow.platform.agriculture.dto;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Used by both AgAnimalController's and AgGroupController's record-weight
 * endpoints — animalId/groupId are supplied by the service from the path,
 * not the body, so this carries only the measurement itself. sampleSize is
 * meaningful for a group (partial-batch sample) and ignored for an animal.
 */
public record RecordWeightRequest(
        @NotNull LocalDate recordedDate,
        @NotNull BigDecimal weightKg,
        Integer sampleSize,
        UUID recordedBy,
        String notes
) {}
