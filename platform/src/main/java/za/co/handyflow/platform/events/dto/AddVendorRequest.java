package za.co.handyflow.platform.events.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record AddVendorRequest(
        @NotBlank @Pattern(regexp = "CATERING|AV_TECH|SECURITY|PHOTOGRAPHY|TRANSPORT|DECOR|ENTERTAINMENT|OTHER",
                message = "must be one of CATERING, AV_TECH, SECURITY, PHOTOGRAPHY, TRANSPORT, DECOR, ENTERTAINMENT, OTHER")
        String vendorType,
        @NotBlank @Size(max = 200) String companyName,
        @Size(max = 200) String contactName,
        @Size(max = 50) String contactPhone,
        @Email @Size(max = 254) String contactEmail,
        @Size(max = 5000) String serviceDescription,
        @PositiveOrZero @Digits(integer = 8, fraction = 2) BigDecimal quotedAmount,
        @Size(max = 5000) String notes
) {}
