package za.co.handyflow.platform.hr.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * An employee as one particular person may see them. Personal data and pay are null unless the caller holds the
 * permission, and the bank account is masked. {@code canSeeSensitive} and {@code canSeeCompensation} tell the screen
 * whether a blank means "not recorded" or "not yours to see".
 */
public record EmployeeViewResponse(
        UUID id, String employeeNumber, String firstName, String lastName, String fullName,
        String email, String phone, String employmentType, String jobTitle, String department,
        LocalDate startDate, LocalDate endDate, String status, UUID managerId, String managerName, UUID userId,
        boolean canSeeSensitive, boolean canSeeCompensation,
        // personal data
        String idNumber, String taxNumber, LocalDate dateOfBirth, String gender, String race,
        String emergencyContactName, String emergencyContactPhone, String terminationReason,
        // pay
        BigDecimal grossSalary, String payFrequency, BigDecimal medicalAidContribution, BigDecimal pensionContribution,
        BigDecimal travelAllowance, String bankName, String bankAccountMasked, String bankBranchCode
) {}
