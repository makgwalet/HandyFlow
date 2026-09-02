package za.co.handyflow.platform.agriculture.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

public record CreateBreedingRecordRequest(
        UUID animalId,
        UUID groupId,
        @NotBlank String breedingType,
        @NotNull LocalDate matingDate,
        UUID sireId,
        String sireDescription,
        LocalDate expectedDueDate,
        String notes
) {}
