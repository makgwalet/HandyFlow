package za.co.handyflow.platform.agriculture.dto;

import java.time.LocalDate;
import java.util.UUID;

public record UpdateAnimalRequest(
        UUID productionAreaId,
        UUID enterpriseId,
        String name,
        String breed,
        LocalDate dateOfBirth,
        boolean estimatedAge,
        UUID sireId,
        UUID damId,
        String notes
) {}
