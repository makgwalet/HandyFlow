package za.co.handyflow.platform.accountant.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Engagement letters, recurring services, workload and WIP (finance F22). */
public final class PracticeServiceDtos {
    private PracticeServiceDtos() {}

    public record LetterRequest(String title, String scope, String exclusions, String feeBasis, BigDecimal feeAmount, String feeNotes,
                                LocalDate startDate, LocalDate endDate) {}
    public record AcceptRequest(String acceptedByName, String method, LocalDate acceptedOn) {}
    public record DeclineRequest(String reason) {}
    public record LetterResponse(UUID id, UUID clientId, int version, String title, String scope, String exclusions, String feeBasis,
                                 BigDecimal feeAmount, String feeNotes, LocalDate startDate, LocalDate endDate, String status,
                                 Instant sentAt, String acceptedByName, String acceptedMethod, LocalDate acceptedOn, String declineReason) {}
    public record LetterText(UUID id, int version, String status, String text) {}

    public record ScheduleRequest(String serviceType, String description, String frequency, LocalDate firstDate, LocalDate endDate,
                                  BigDecimal feeAmount, boolean includeVat, Integer dueDays, BigDecimal budgetHours, UUID ownerUserId) {}
    public record ScheduleResponse(UUID id, UUID clientId, String clientName, String serviceType, String description, String frequency,
                                   LocalDate firstDate, LocalDate endDate, BigDecimal feeAmount, boolean includeVat, int dueDays,
                                   BigDecimal budgetHours, UUID ownerUserId, String ownerName, String status, LocalDate nextRun) {}
    public record RunResponse(UUID id, UUID scheduleId, UUID clientId, String clientName, String serviceType, String description,
                              LocalDate runDate, String status, BigDecimal feeAmount, UUID feeNoteId, String note) {}
    public record SkipRequest(String note) {}
    public record GenerateResult(int made) {}

    public record WorkloadRow(UUID userId, String name, int clientsPrepared, int clientsReviewed, int clientsPartner,
                              int deadlinesNext30Days, int deadlinesOverdue, BigDecimal hoursLast30Days, BigDecimal unbilledHours,
                              BigDecimal unbilledValue, int serviceRunsDue) {}
    public record WipRow(UUID clientId, String clientName, BigDecimal unbilledHours, BigDecimal unbilledValue, BigDecimal current,
                         BigDecimal days31to60, BigDecimal days61to90, BigDecimal over90, LocalDate oldest) {}
    public record WipReport(LocalDate asOf, List<WipRow> rows, BigDecimal totalValue, BigDecimal totalOver60) {}
    public record ProfitRow(UUID clientId, String clientName, BigDecimal hours, BigDecimal standardValue, BigDecimal billed,
                            BigDecimal realisationPercent, BigDecimal budgetHours, BigDecimal budgetUsedPercent) {}
    public record ProfitReport(LocalDate from, LocalDate to, List<ProfitRow> rows, BigDecimal totalHours, BigDecimal totalStandardValue, BigDecimal totalBilled) {}
}
