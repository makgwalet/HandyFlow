package za.co.handyflow.platform.internalaudit.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class AuditMonitoringDtos {
    private AuditMonitoringDtos() {}

    public record ScanRequest(BigDecimal threshold, Integer backdatedDays, Integer duplicateWindowDays, Integer lookbackDays) {}
    public record ReviewRequest(String status, String note, UUID findingId) {}

    public record AlertView(UUID id, String rule, String severity, String subjectType, List<String> subjectIds, String summary, String detail, BigDecimal amount, LocalDate occurredOn,
                            String status, Instant firstSeen, Instant lastSeen, String reviewedByName, Instant reviewedAt, String disposition, UUID findingId) {}
    public record Counts(int open, int high, int medium, int low, int inReview, int dismissed, int escalated) {}
    public record Alerts(Counts counts, List<AlertView> alerts, String notice) {}
    public record ScanResult(int found, int newAlerts, int seenAgain, Map<String, Integer> byRule, int journalsChecked, int bankPaymentsChecked, int peopleChecked) {}
}
