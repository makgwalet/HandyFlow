package za.co.handyflow.platform.security.dto;

import jakarta.validation.constraints.*;
import java.time.LocalDate;

public record SetGradeRateRequest(
        @NotBlank String grade,                  // A | B | C | D | E
        @NotNull @Min(1) Integer hourlyRateCents, // ZAR cents
        Integer standardHoursPerDay,              // null → defaults to 9
        @NotNull LocalDate effectiveFrom
) {}
