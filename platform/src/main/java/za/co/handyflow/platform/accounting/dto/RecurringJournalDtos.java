package za.co.handyflow.platform.accounting.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Requests and responses for recurring journals, accruals and prepayments (finance F17). */
public final class RecurringJournalDtos {

    private RecurringJournalDtos() {}

    public record LineInput(UUID accountId, String description, BigDecimal debit, BigDecimal credit) {}

    public record CreateRequest(
            String name, String kind, String frequency, LocalDate startDate, LocalDate endDate, Integer runs,
            String description, String reference, List<LineInput> lines,
            BigDecimal totalAmount, UUID expenseAccountId, UUID prepaidAccountId) {}

    public record RunSummary(int runIndex, LocalDate runDate, UUID journalId, UUID reversalJournalId, BigDecimal amount) {}

    public record Response(
            UUID id, String name, String kind, String frequency, LocalDate startDate, LocalDate endDate, Integer runsTotal,
            int runsDone, String status, String description, String reference, List<LineInput> lines,
            BigDecimal totalAmount, UUID expenseAccountId, UUID prepaidAccountId,
            LocalDate nextRunDate, List<LocalDate> upcoming, String lastError, List<RunSummary> runs) {}

    public record RunResult(int made, List<String> problems) {}
}
