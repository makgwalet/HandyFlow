package za.co.handyflow.platform.hr.dto;

import java.math.BigDecimal;
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
        Instant createdAt,
        int spouses,
        int children,
        int otherDependants,
        BigDecimal employeeContribution,
        BigDecimal employerContribution
) {
    /** Without the split or the contributions. */
    public MedicalSchemeResponse(UUID id, UUID employeeId, String schemeName, String memberNumber, int dependants, int peopleCovered,
                                 LocalDate effectiveFrom, LocalDate effectiveTo, boolean current, String notes, Instant createdAt) {
        this(id, employeeId, schemeName, memberNumber, dependants, peopleCovered, effectiveFrom, effectiveTo, current, notes, createdAt,
                0, 0, dependants, null, null);
    }
}
