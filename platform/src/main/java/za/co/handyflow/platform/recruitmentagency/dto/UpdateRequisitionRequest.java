package za.co.handyflow.platform.recruitmentagency.dto;

import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.time.LocalDate;

public record UpdateRequisitionRequest(
        @NotBlank String title,
        String description,
        BigDecimal salaryMin,
        BigDecimal salaryMax,
        String location,
        String employmentType,
        LocalDate targetStartDate,
        String notes
) {}