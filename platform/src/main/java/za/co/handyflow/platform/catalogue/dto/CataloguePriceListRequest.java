package za.co.handyflow.platform.catalogue.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Code is fixed once the list exists; name, order and on/off can change. */
public record CataloguePriceListRequest(
        @NotBlank @Size(max = 30) String code,
        @NotBlank @Size(max = 60) String name,
        Integer sortOrder,
        Boolean active) {}
