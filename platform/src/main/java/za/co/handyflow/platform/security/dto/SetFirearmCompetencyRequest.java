package za.co.handyflow.platform.security.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record SetFirearmCompetencyRequest(
        @NotBlank String competencyNumber,
        @NotNull  LocalDate expiry
) {}
