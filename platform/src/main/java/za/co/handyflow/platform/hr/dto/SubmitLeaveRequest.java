package za.co.handyflow.platform.hr.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record SubmitLeaveRequest(
        @NotBlank String leaveType,
        @NotNull  LocalDate startDate,
        @NotNull  LocalDate endDate,
        String reason,
        Boolean halfDay,
        Boolean medicalCertificate
) {
    public SubmitLeaveRequest(String leaveType, LocalDate startDate, LocalDate endDate, String reason) {
        this(leaveType, startDate, endDate, reason, null, null);
    }
}