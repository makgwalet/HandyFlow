package za.co.handyflow.platform.hr;

import za.co.handyflow.platform.hr.dto.EmployeeResponse;

import java.util.UUID;

/**
 * What other modules may know about an employee: who they are and how to reach them. Deliberately without the ID number, tax
 * number, date of birth, race, salary, allowances or emergency contact, which only HR's own screens and permissions show.
 */
public record EmployeeSummary(
        UUID id,
        String employeeNumber,
        String firstName,
        String lastName,
        String fullName,
        String email,
        String phone,
        String jobTitle,
        String department,
        String status,
        UUID managerId,
        UUID userId
) {
    /** Without the reporting line or login link, for callers that do not need them. */
    public EmployeeSummary(UUID id, String employeeNumber, String firstName, String lastName, String fullName, String email, String phone,
                           String jobTitle, String department, String status) {
        this(id, employeeNumber, firstName, lastName, fullName, email, phone, jobTitle, department, status, null, null);
    }

    public static EmployeeSummary of(za.co.handyflow.platform.hr.domain.model.HrEmployee e) {
        return new EmployeeSummary(e.getId(), e.getEmployeeNumber(), e.getFirstName(), e.getLastName(), e.getFullName(), e.getEmail(), e.getPhone(),
                e.getJobTitle(), e.getDepartment(), e.getStatus(), e.getManagerId(), e.getUserId());
    }

    public static EmployeeSummary from(EmployeeResponse e) {
        return new EmployeeSummary(e.id(), e.employeeNumber(), e.firstName(), e.lastName(), e.fullName(), e.email(), e.phone(),
                e.jobTitle(), e.department(), e.status());
    }
}
