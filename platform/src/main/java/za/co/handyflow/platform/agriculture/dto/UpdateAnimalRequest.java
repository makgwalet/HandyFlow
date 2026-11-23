package za.co.handyflow.platform.agriculture.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * A field left out (null) keeps its current value. To empty a field, list its name in {@code clear}
 * (productionAreaId, enterpriseId, name, breed, dateOfBirth, sireId, damId, notes).
 */
public record UpdateAnimalRequest(
        UUID productionAreaId,
        UUID enterpriseId,
        String name,
        String breed,
        LocalDate dateOfBirth,
        Boolean estimatedAge,
        UUID sireId,
        UUID damId,
        String notes,
        List<String> clear
) {}
