package za.co.handyflow.platform.agriculture.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
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
        Integer stillbornCount,
        String notes,
        Instant createdAt,
        Instant updatedAt,
        /** The animals a recorded birth created, in the order entered; empty when none were created. */
        List<UUID> offspringAnimalIds
) {
    /** Without the created animals (callers written before births created them). */
    public BreedingRecordResponse(UUID id, UUID animalId, UUID groupId, String breedingType, LocalDate matingDate, UUID sireId,
                                  String sireDescription, LocalDate expectedDueDate, LocalDate actualBirthDate, String outcome,
                                  Integer offspringCount, Integer stillbornCount, String notes, Instant createdAt, Instant updatedAt) {
        this(id, animalId, groupId, breedingType, matingDate, sireId, sireDescription, expectedDueDate, actualBirthDate, outcome,
                offspringCount, stillbornCount, notes, createdAt, updatedAt, List.of());
    }
}
