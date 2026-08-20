package za.co.handyflow.platform.payrollbureau.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

// Mirrors hr.HrService.updateEmployee()'s shape — that's the established
// precedent for "editing an already-created employee" in this codebase,
// scoped down to the fields PayEmployee actually has (no jobTitle/
// department — PayEmployee's own class Javadoc deliberately excludes the
// full HR lifecycle, only what PayrollBureauEngine needs to run payroll).
public record UpdatePayEmployeeRequest(
        @NotBlank String firstName,
        @NotBlank String lastName,
        String idNumber,
        String taxNumber,
        LocalDate dateOfBirth,
        @Email String email,
        String phone,
        @NotNull BigDecimal grossSalary,
        BigDecimal travelAllowance,
        BigDecimal pensionContribution,
        BigDecimal medicalAidContribution,
        String bankName,
        String bankAccountNumber,
        String bankBranchCode
) {}