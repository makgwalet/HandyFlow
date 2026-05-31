package za.co.handyflow.platform.events.dto;

import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;

public record AddVendorRequest(
        @NotBlank String vendorType,
        @NotBlank String companyName,
        String contactName,
        String contactPhone,
        String contactEmail,
        String serviceDescription,
        BigDecimal quotedAmount,
        String notes
) {}