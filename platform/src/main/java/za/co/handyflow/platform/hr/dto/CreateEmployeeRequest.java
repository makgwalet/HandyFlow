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
        String notes,
        String emergencyContactRelation
) {
    /** Without the emergency contact's relation, as the request was before it could be given. */
    public CreateEmployeeRequest(String firstName, String lastName, String idNumber, String taxNumber, LocalDate dateOfBirth, String gender, String race,
                                 String email, String phone, LocalDate startDate, String employmentType, String jobTitle, String department,
                                 BigDecimal grossSalary, String payFrequency, String bankName, String bankAccountNumber, String bankBranchCode,
                                 BigDecimal medicalAidContribution, BigDecimal pensionContribution, BigDecimal travelAllowance,
                                 String emergencyContactName, String emergencyContactPhone, String notes) {
        this(firstName, lastName, idNumber, taxNumber, dateOfBirth, gender, race, email, phone, startDate, employmentType, jobTitle, department,
                grossSalary, payFrequency, bankName, bankAccountNumber, bankBranchCode, medicalAidContribution, pensionContribution, travelAllowance,
                emergencyContactName, emergencyContactPhone, notes, null);
    }
}