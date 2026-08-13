package za.co.handyflow.platform.recruitmentagency.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

public record CreateRequisitionRequest(
        @NotNull java.util.UUID clientId,
        @NotBlank String title,
        String description,
        BigDecimal salaryMin,
        BigDecimal salaryMax,
        String location,
        String employmentType,
        LocalDate targetStartDate
) {}