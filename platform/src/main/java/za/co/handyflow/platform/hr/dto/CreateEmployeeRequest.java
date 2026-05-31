package za.co.handyflow.platform.hr.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

public record CreateEmployeeRequest(
        @NotBlank String firstName,
        @NotBlank String lastName,
        String idNumber,
        String taxNumber,
        LocalDate dateOfBirth,
        String gender,
        String race,
        String email,
        String phone,
        @NotNull LocalDate startDate,
        String employmentType,
        String jobTitle,
        String department,
        @NotNull BigDecimal grossSalary,
        String payFrequency,
        String bankName,
        String bankAccountNumber,
        String bankBranchCode,
        BigDecimal medicalAidContribution,
        BigDecimal pensionContribution,
        BigDecimal travelAllowance,
        String emergencyContactName,
        String emergencyContactPhone,
        String notes
) {}