package za.co.handyflow.platform.fleet.dto;

import jakarta.validation.constraints.NotBlank;
import java.time.LocalDate;

public record UpdateDriverRequest(
        @NotBlank String firstName,
        @NotBlank String lastName,
        String phone,
        String email,
        String idNumber,
        String licenseNumber,
        String licenseCode,
        LocalDate licenseExpiry,
        boolean prdpRequired,
        String prdpNumber,
        String prdpCategory,
        LocalDate prdpExpiry,
        String notes
) {}
