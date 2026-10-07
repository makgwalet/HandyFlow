package za.co.handyflow.platform.compliancetender.dto;

import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.time.LocalDate;

/** The details of a tender that can be corrected after it is created. The tender number and status are not part of it. */
public record UpdateTenderRequest(
        @NotBlank String name, String tenderAuthority, String authorityReferenceNumber,
        LocalDate closingDate, LocalDate briefingDate, LocalDate siteInspectionDate,
        BigDecimal estimatedValue, String industry, String requiredClassOfWork,
        /** null leaves the current setting unchanged. */ Boolean requiresPricing
) {}
