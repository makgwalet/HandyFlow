package za.co.handyflow.platform.hr.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A new stretch of membership. Give either {@code dependants} (people covered besides the employee) on its own, or the
 * split into {@code spouses}, {@code children} and {@code otherDependants}; if you give both they must agree.
 * Contributions are monthly amounts and optional.
 */
public record MedicalSchemeRequest(
        @NotBlank(message = "The scheme name is required") @Size(max = 120) String schemeName,
        @Size(max = 60) String memberNumber,
        Integer dependants,
        @NotNull(message = "The start date is required") LocalDate effectiveFrom,
        @Size(max = 500) String notes,
        Integer spouses,
        Integer children,
        Integer otherDependants,
        BigDecimal employeeContribution,
        BigDecimal employerContribution
) {
    /** The request as it was before the split and the contributions existed. */
    public MedicalSchemeRequest(String schemeName, String memberNumber, Integer dependants, LocalDate effectiveFrom, String notes) {
        this(schemeName, memberNumber, dependants, effectiveFrom, notes, null, null, null, null, null);
    }
}
