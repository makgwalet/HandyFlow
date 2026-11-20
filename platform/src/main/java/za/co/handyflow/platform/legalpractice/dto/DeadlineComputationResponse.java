package za.co.handyflow.platform.legalpractice.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * {@code current} is true for the newest computation of its key date. {@code drifted} is true when the key date's due date
 * is no longer the one this computation produced (someone edited it by hand since).
 */
public record DeadlineComputationResponse(UUID id, UUID matterId, UUID keyDateId, UUID supersedesId, boolean current,
                                          String ruleCode, String ruleName, int amount, String unit, String direction,
                                          String rollover, String recessStart, String recessEnd, String triggerLabel,
                                          LocalDate triggerDate, LocalDate dueDate, List<String> explanation, String reason,
                                          String computedByName, Instant computedAt, LocalDate keyDateDueDate,
                                          String keyDateStatus, boolean drifted) {}
