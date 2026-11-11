package za.co.handyflow.platform.events.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Post-event reconciliation: attendance vs registrations, the final cost position, and what is still open. */
public record CloseOutResponse(
        UUID eventId,
        String eventNumber,
        String title,
        String status,
        AttendanceSummary attendance,
        BudgetSummaryResponse budget,          // budgetStatus is NOT_STARTED when the event has no budget
        List<SupplierSummary> suppliers,
        List<CloseOutCheck> checks,
        boolean readyToClose,                  // every check passed
        Instant generatedAt
) {
    public record AttendanceSummary(
            long registered,
            long checkedIn,
            long notCheckedIn,                 // once the event is over, these are the no-shows
            long walkIns,
            long cancelled,
            long transferred,
            BigDecimal attendanceRatePercent   // checkedIn / registered; null when nobody registered
    ) {}

    public record CloseOutCheck(String code, String label, boolean passed, String detail) {}
}