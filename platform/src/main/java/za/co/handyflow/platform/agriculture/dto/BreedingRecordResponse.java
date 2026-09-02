package za.co.handyflow.platform.agriculture.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record BreedingRecordResponse(
        UUID id,
        UUID animalId,
        UUID groupId,
        String breedingType,
        LocalDate matingDate,
        UUID sireId,
        String sireDescription,
        LocalDate expectedDueDate,
        LocalDate actualBirthDate,
        String outcome,
        Integer offspringCount,
        String notes,
        Instant createdAt,
        Instant updatedAt
) {}
