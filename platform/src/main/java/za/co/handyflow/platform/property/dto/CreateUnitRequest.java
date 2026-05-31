package za.co.handyflow.platform.property.dto;
import jakarta.validation.constraints.NotBlank; import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal; import java.util.List;
public record CreateUnitRequest(
        @NotBlank String unitNumber, @NotBlank String unitType,
        Integer floorNumber, BigDecimal sizeSqm,
        @NotNull BigDecimal baseRent, BigDecimal depositAmount,
        boolean furnished, List<String> amenities
) {}