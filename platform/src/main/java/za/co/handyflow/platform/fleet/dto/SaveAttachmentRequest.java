package za.co.handyflow.platform.fleet.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Register (POST) or edit (PUT, full replace) an attachment / implement. */
public record SaveAttachmentRequest(
        @NotBlank @Size(max = 150) String name,
        @Size(max = 50) String category,         // e.g. PLOUGH, SLASHER, FORKS, BUCKET
        @Size(max = 100) String serialNumber,
        String description,
        LocalDate purchaseDate,
        @PositiveOrZero BigDecimal purchasePrice,
        String notes
) {}