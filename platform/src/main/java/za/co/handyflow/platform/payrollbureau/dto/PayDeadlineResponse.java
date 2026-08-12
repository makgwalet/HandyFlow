package za.co.handyflow.platform.payrollbureau.dto;

import java.time.LocalDate;
import java.util.UUID;

public record PayDeadlineResponse(
        UUID id, String deadlineType, int periodYear, Integer periodMonth,
        LocalDate adjustedDueDate, String status, LocalDate filedDate, long daysUntilDue
) {}