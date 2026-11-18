package za.co.handyflow.platform.contracting.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** The portfolio report. Every figure is calculated from recorded data; where something is missing it is counted, not guessed. */
public final class ContractReportDtos {
    private ContractReportDtos() {}

    public record SlowContract(String contractNumber, String title, double days) {}

    /** Days from sending a contract for signature to the last signature. {@code count} is 0 when nothing was signed in the window. */
    public record Turnaround(long count, double averageDays, double medianDays, double p90Days, List<SlowContract> slowest) {}

    /** One row per contract type and currency: values in different currencies are never added together. */
    public record TypeValue(String contractType, String currency, long contracts, BigDecimal totalValue, long withoutValue) {}

    /** One calendar month of the expiry profile (month is yyyy-MM). */
    public record ExpiryMonth(String month, long contracts, Map<String, BigDecimal> valueByCurrency) {}

    /** Obligations due in the window. {@code onTimeRate} is completed-on-time over all completed, or null when none were completed. */
    public record ObligationStats(long due, long completedOnTime, long completedLate, long waived, long stillOpenOverdue, long stillOpenNotYetDue, Double onTimeRate) {}

    public record RenewalStats(Map<String, Long> decisionsByType, long noticePassedWithoutDecision) {}

    /** Published templates by legal status: APPROVED, CONDITIONAL, EXPIRED, WITHDRAWN, REJECTED, NOT_REVIEWED. */
    public record TemplateCurrency(Map<String, Long> publishedByLegalStatus) {}

    public record PortfolioReport(LocalDate from, LocalDate to, Turnaround turnaround, List<TypeValue> signedByType,
                                  List<ExpiryMonth> expiryProfile, ObligationStats obligations, RenewalStats renewals, TemplateCurrency templates) {}
}
