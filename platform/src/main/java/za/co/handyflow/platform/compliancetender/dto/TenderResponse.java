package za.co.handyflow.platform.compliancetender.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record TenderResponse(
        UUID id, String tenderNumber, String name, String tenderAuthority, String authorityReferenceNumber,
        LocalDate closingDate, LocalDate briefingDate, LocalDate siteInspectionDate, BigDecimal estimatedValue,
        String industry, String requiredClassOfWork, String status, String outcomeReason,
        BigDecimal awardedValue, Instant submittedAt, Instant createdAt
) {}
