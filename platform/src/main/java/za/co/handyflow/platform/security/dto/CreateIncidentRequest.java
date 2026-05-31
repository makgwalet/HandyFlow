package za.co.handyflow.platform.security.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateIncidentRequest(
        @NotNull UUID    siteId,
        UUID             shiftId,
        UUID             guardId,
        @NotBlank String title,
        String           description,
        @NotNull
        @Pattern(regexp = "LOW|MEDIUM|HIGH|CRITICAL",
                message = "severity must be LOW, MEDIUM, HIGH or CRITICAL")
        String           severity,
        BigDecimal latitude,    // was Double — caused compile error
        BigDecimal       longitude    // was Double — caused compile error
) {}