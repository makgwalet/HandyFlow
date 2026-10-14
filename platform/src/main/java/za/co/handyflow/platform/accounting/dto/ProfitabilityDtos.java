package za.co.handyflow.platform.accounting.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Profitability by cost centre and automatic tagging (finance F28). */
public final class ProfitabilityDtos {
    private ProfitabilityDtos() {}

    /** centreId null for the unallocated and total rows. expenses is by account subtype, so the screen can choose what counts as direct. */
    public record Row(UUID centreId, String code, String name, String kind, BigDecimal income, Map<String, BigDecimal> expenses, BigDecimal expenseTotal, BigDecimal profit, BigDecimal marginPercent) {}
    public record View(LocalDate from, LocalDate to, boolean rollUp, String kind, List<Row> rows, Row unallocated, Row total, List<String> subtypes) {}
    public record Month(LocalDate month, BigDecimal income, Map<String, BigDecimal> expenses, BigDecimal expenseTotal, BigDecimal profit) {}
    public record Trend(UUID centreId, String code, String name, List<Month> months) {}

    public record RuleSave(String name, Integer priority, String accountCode, String textContains, String entryType, UUID costCentreId) {}
    public record Rule(UUID id, String name, int priority, String accountCode, String textContains, String entryType, UUID costCentreId, String costCentreName, boolean active) {}
    public record SetActive(boolean active) {}

    public record ApplyRequest(LocalDate from, LocalDate to, boolean apply) {}
    public record Sample(LocalDate date, String entryNumber, String accountCode, String description, String ruleName, String costCentre) {}
    public record ApplyResult(boolean applied, int matched, boolean limitReached, Map<String, Integer> byRule, List<Sample> samples) {}
}
