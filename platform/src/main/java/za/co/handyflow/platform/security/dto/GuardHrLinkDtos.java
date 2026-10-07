// security/dto/GuardHrLinkDtos.java
package za.co.handyflow.platform.security.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** The link between a guard and an HR employee record. HR owns the employee; Security stores only the id. */
public final class GuardHrLinkDtos {
    private GuardHrLinkDtos() {}

    public record LinkRequest(@NotNull UUID employeeId) {}

    /** `linked` is false when the guard has no HR record; `employeeMissing` when the linked record can no longer be found. */
    public record HrLink(boolean linked, boolean employeeMissing, UUID employeeId, String employeeNumber, String fullName,
                         String jobTitle, String department, String status) {}

    public record EmployeeOption(UUID id, String employeeNumber, String fullName, String jobTitle, String status) {}
}
