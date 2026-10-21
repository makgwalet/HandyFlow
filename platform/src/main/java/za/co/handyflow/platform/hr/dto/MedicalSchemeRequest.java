package za.co.handyflow.platform.hr.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** A new stretch of membership. {@code dependants} counts people covered besides the employee. */
public record MedicalSchemeRequest(
        @NotBlank(message = "The scheme name is required") @Size(max = 120) String schemeName,
        @Size(max = 60) String memberNumber,
        @NotNull(message = "Dependants is required") Integer dependants,
        @NotNull(message = "The start date is required") LocalDate effectiveFrom,
        @Size(max = 500) String notes
) {}
