package za.co.handyflow.platform.payrollbureau.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

public record CreatePayEmployeeRequest(
        @NotBlank String firstName,
        @NotBlank String lastName,
        String idNumber,
        LocalDate dateOfBirth,
        @NotNull BigDecimal grossSalary,
        BigDecimal travelAllowance,
        BigDecimal pensionContribution,
        BigDecimal medicalAidContribution,
        String bankName,
        String bankAccountNumber,
        String bankBranchCode,
        @NotNull LocalDate startDate
) {}