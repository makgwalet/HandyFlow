package za.co.handyflow.platform.fleet.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Register (POST) or edit (PUT, full replace) a tyre. The serial number must be unique within the tenant. */
public record SaveTyreRequest(
        @NotBlank @Size(max = 100) String serialNumber,
        @NotBlank @Size(max = 100) String brand,
        @Size(max = 100) String pattern,
        @NotBlank @Size(max = 30) String size,            // e.g. 295/80R22.5
        LocalDate purchaseDate,
        @PositiveOrZero BigDecimal purchasePrice,
        @Size(max = 255) String supplier,
        @Positive @DecimalMax("30.0") BigDecimal initialTreadDepthMm,   // tread when new; also the first reading
        String notes
) {}