package za.co.handyflow.platform.compliancetender.dto;

import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.time.LocalDate;

public record CreateTenderRequest(
        @NotBlank String name, String tenderAuthority, String authorityReferenceNumber,
        LocalDate closingDate, LocalDate briefingDate, LocalDate siteInspectionDate,
        BigDecimal estimatedValue, String industry, String requiredClassOfWork
) {}
