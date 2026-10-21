package za.co.handyflow.platform.hr.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record MedicalSchemeResponse(
        UUID id,
        UUID employeeId,
        String schemeName,
        String memberNumber,
        int dependants,
        int peopleCovered,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        boolean current,
        String notes,
        Instant createdAt
) {}
