package za.co.handyflow.platform.fleet.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record CreateDriverRequest(
        @NotBlank String firstName,
        @NotBlank String lastName,
        String phone,
        String email,
        String idNumber,
        String licenseNumber,
        String licenseCode,         // A, A1, B, C1, C, EB, EC1, EC
        LocalDate licenseExpiry,
        boolean prdpRequired,
        String prdpNumber,
        String prdpCategory,        // G, P, D
        LocalDate prdpExpiry,
        String notes,
        @Size(max = 100) String department   // optional: Operations, Logistics, ...
) {}
