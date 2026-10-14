package za.co.handyflow.platform.bookkeeping.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Bank rules, categorising, client queries and handover (finance F19). */
public final class BkWorkDtos {

    private BkWorkDtos() {}

    public record RuleRequest(String name, UUID bankAccountId, String direction, String text, String field,
                              BigDecimal min, BigDecimal max, Integer priority, UUID contraAccountId) {}

    public record RuleResponse(UUID id, String name, UUID bankAccountId, String direction, String text, String field,
                               BigDecimal min, BigDecimal max, int priority, boolean active, UUID contraAccountId) {}

    public record Suggestion(UUID transactionId, LocalDate date, String description, BigDecimal amount, String type,
                             UUID ruleId, String ruleName, UUID contraAccountId) {}

    public record CategoriseItem(UUID transactionId, UUID contraAccountId) {}

    public record CategoriseRequest(List<CategoriseItem> items) {}

    public record CategoriseResponse(int done) {}

    public record QueryRequest(String kind, UUID transactionId, String question) {}

    public record AnswerRequest(String answer, String documentRef) {}

    public record QueryResponse(UUID id, UUID clientId, UUID transactionId, String kind, String question, String status, String answer,
                                String documentRef, Instant askedAt, Instant answeredAt, long daysOpen) {}

    public record WorkQueueRow(UUID clientId, String clientName, long unreconciled, LocalDate oldestUnreconciled, long openQueries,
                               long missingDocuments, long draftJournals) {}

    public record HandoverRequest(LocalDate periodEnd, String note) {}

    public record RespondRequest(String note) {}

    public record HandoverResponse(UUID id, UUID clientId, LocalDate periodEnd, String status, int openQueries, String note,
                                   Instant sentAt, Instant respondedAt, String responseNote, List<String> warnings) {}
}
