package za.co.handyflow.platform.hr.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record EmployeeChangeResponse(
        UUID id, UUID employeeId, String employeeName, String changeType, List<Field> fields,
        LocalDate effectiveFrom, String reason, String status,
        UUID requestedBy, Instant requestedAt, UUID decidedBy, Instant decidedAt, String decisionNote,
        boolean selfApproved, Instant appliedAt
) {
    /** One field that changes. Account numbers are masked. */
    public record Field(String key, String label, String oldValue, String newValue) {}
}
