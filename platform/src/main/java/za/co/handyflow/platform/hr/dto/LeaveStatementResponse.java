package za.co.handyflow.platform.hr.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Balances for a year and every movement behind them. */
public record LeaveStatementResponse(UUID employeeId, String employeeName, int year, List<LeaveBalanceResponse> balances, List<Entry> entries) {
    public record Entry(UUID id, String leaveType, LocalDate entryDate, String kind, BigDecimal days, String note,
                        UUID requestId, UUID createdBy, Instant createdAt) {}
}
