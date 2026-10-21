package za.co.handyflow.platform.catalogue.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import za.co.handyflow.platform.catalogue.domain.model.UnitKind;

/** Create or edit a unit. The code cannot be changed after creation. */
public record CatalogueUnitRequest(
        @NotBlank(message = "The unit code is required") @Size(max = 50) String code,
        @Size(max = 60) String name,
        UnitKind kind,
        Integer decimals,
        Boolean active) {}
