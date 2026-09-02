package za.co.handyflow.platform.legalpractice.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * {@code employeeName}/{@code employeeEmail} are populated via a best-effort,
 * defensive {@code HrFacade.findEmployeeById()} lookup when {@code employeeId}
 * is set — both are null when there's no HR link, or when the linked
 * employee record can't be found (HR is never a hard dependency here).
 */
public record LpAttorneyResponse(
        UUID id,
        String name,
        String email,
        String phone,
        String role,
        String admissionNumber,
        BigDecimal hourlyRate,
        UUID employeeId,
        String employeeName,
        String employeeEmail,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {}
