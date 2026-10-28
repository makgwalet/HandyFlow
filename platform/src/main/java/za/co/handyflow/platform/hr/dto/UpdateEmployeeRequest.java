package za.co.handyflow.platform.hr.dto;

import java.time.LocalDate;

/**
 * Details that may be edited directly. Pay and bank details are not here: they change through an approved request.
 * Personal data fields are applied only for a caller with HR_SENSITIVE_READ; null leaves a value as it is. The {@code foreign} block
 * (passport and work permit) replaces all six values when it is sent and changes nothing when it is null.
 */
public record UpdateEmployeeRequest(
        String email, String phone, String employmentType, String jobTitle, String department,
        String emergencyContactName, String emergencyContactPhone, String notes,
        String idNumber, String taxNumber, LocalDate dateOfBirth, String gender, String race,
        ForeignIdentity foreign
) {
    /** Without the passport and permit block, which then stays as it is. */
    public UpdateEmployeeRequest(String email, String phone, String employmentType, String jobTitle, String department,
                                 String emergencyContactName, String emergencyContactPhone, String notes,
                                 String idNumber, String taxNumber, LocalDate dateOfBirth, String gender, String race) {
        this(email, phone, employmentType, jobTitle, department, emergencyContactName, emergencyContactPhone, notes,
                idNumber, taxNumber, dateOfBirth, gender, race, null);
    }
}
