package za.co.handyflow.platform.hr.dto;

import java.time.LocalDate;

/**
 * Details that may be edited directly. Pay and bank details are not here: they change through an approved request.
 * Personal data fields are applied only for a caller with HR_SENSITIVE_READ; null leaves a value as it is.
 */
public record UpdateEmployeeRequest(
        String email, String phone, String employmentType, String jobTitle, String department,
        String emergencyContactName, String emergencyContactPhone, String notes,
        String idNumber, String taxNumber, LocalDate dateOfBirth, String gender, String race
) {}
