package za.co.handyflow.platform.hr.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record LeaveRequestResponse(
        UUID id,
        UUID employeeId,
        String employeeName,
        String leaveType,
        LocalDate startDate,
        LocalDate endDate,
        BigDecimal daysRequested,
        String reason,
        String status,
        String rejectionReason,
        Instant createdAt,
        boolean halfDay,
        boolean medicalCertificate,
        boolean certificateExpected
) {
    /** The shape before half days and certificates, kept for callers that build a response themselves. */
    public LeaveRequestResponse(UUID id, UUID employeeId, String employeeName, String leaveType, LocalDate startDate,
                                LocalDate endDate, BigDecimal daysRequested, String reason, String status,
                                String rejectionReason, Instant createdAt) {
        this(id, employeeId, employeeName, leaveType, startDate, endDate, daysRequested, reason, status, rejectionReason,
                createdAt, false, false, false);
    }
}