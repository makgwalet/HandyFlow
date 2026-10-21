package za.co.handyflow.platform.catalogue.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import za.co.handyflow.platform.catalogue.domain.model.AttributeType;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Create or edit an attribute definition. On edit the code, type and category are ignored (they are fixed).
 * categoryId null means the attribute applies to every item.
 */
public record CatalogueAttributeRequest(
        UUID categoryId,
        @NotBlank(message = "The attribute code is required") @Size(max = 40) String code,
        @NotBlank(message = "The attribute name is required") @Size(max = 60) String label,
        @NotNull(message = "Choose a type") AttributeType type,
        Boolean required,
        List<String> options,
        BigDecimal min,
        BigDecimal max,
        Integer maxLength,
        Integer sortOrder,
        Boolean active) {}
