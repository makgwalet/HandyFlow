package za.co.handyflow.platform.hr.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record LeaveBalanceResponse(
        UUID id,
        String leaveType,
        int leaveYear,
        BigDecimal entitledDays,
        BigDecimal takenDays,
        BigDecimal pendingDays,
        BigDecimal availableDays
) {}