package za.co.handyflow.platform.hr.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record AddDisciplinaryRequest(
        @NotNull  LocalDate incidentDate,
        @NotBlank String incidentType,
        @NotBlank String description,
        LocalDate hearingDate
) {}