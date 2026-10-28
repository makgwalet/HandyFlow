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
        BigDecimal travelAllowance, String bankName, String bankAccountMasked, String bankBranchCode,
        // passport and work permit: personal data, so null without the sensitive permission
        ForeignIdentity foreign,
        // pay: the kind of bank account (CURRENT, SAVINGS, TRANSMISSION, BOND)
        String bankAccountType
) {
    /** Without the account type, as before it was kept. */
    public EmployeeViewResponse(UUID id, String employeeNumber, String firstName, String lastName, String fullName,
                                String email, String phone, String employmentType, String jobTitle, String department,
                                LocalDate startDate, LocalDate endDate, String status, UUID managerId, String managerName, UUID userId,
                                boolean canSeeSensitive, boolean canSeeCompensation,
                                String idNumber, String taxNumber, LocalDate dateOfBirth, String gender, String race,
                                String emergencyContactName, String emergencyContactPhone, String terminationReason,
                                BigDecimal grossSalary, String payFrequency, BigDecimal medicalAidContribution, BigDecimal pensionContribution,
                                BigDecimal travelAllowance, String bankName, String bankAccountMasked, String bankBranchCode, ForeignIdentity foreign) {
        this(id, employeeNumber, firstName, lastName, fullName, email, phone, employmentType, jobTitle, department, startDate, endDate, status,
                managerId, managerName, userId, canSeeSensitive, canSeeCompensation, idNumber, taxNumber, dateOfBirth, gender, race,
                emergencyContactName, emergencyContactPhone, terminationReason, grossSalary, payFrequency, medicalAidContribution,
                pensionContribution, travelAllowance, bankName, bankAccountMasked, bankBranchCode, foreign, null);
    }

    /** Without the passport and permit block, as before they were kept. */
    public EmployeeViewResponse(UUID id, String employeeNumber, String firstName, String lastName, String fullName,
                                String email, String phone, String employmentType, String jobTitle, String department,
                                LocalDate startDate, LocalDate endDate, String status, UUID managerId, String managerName, UUID userId,
                                boolean canSeeSensitive, boolean canSeeCompensation,
                                String idNumber, String taxNumber, LocalDate dateOfBirth, String gender, String race,
                                String emergencyContactName, String emergencyContactPhone, String terminationReason,
                                BigDecimal grossSalary, String payFrequency, BigDecimal medicalAidContribution, BigDecimal pensionContribution,
                                BigDecimal travelAllowance, String bankName, String bankAccountMasked, String bankBranchCode) {
        this(id, employeeNumber, firstName, lastName, fullName, email, phone, employmentType, jobTitle, department, startDate, endDate, status,
                managerId, managerName, userId, canSeeSensitive, canSeeCompensation, idNumber, taxNumber, dateOfBirth, gender, race,
                emergencyContactName, emergencyContactPhone, terminationReason, grossSalary, payFrequency, medicalAidContribution,
                pensionContribution, travelAllowance, bankName, bankAccountMasked, bankBranchCode, null, null);
    }
}
