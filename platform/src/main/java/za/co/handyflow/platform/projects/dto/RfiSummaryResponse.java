package za.co.handyflow.platform.projects.dto;

import java.util.Map;
import java.util.UUID;

/**
 * @param awaitingResponse      SUBMITTED — someone still owes an answer
 * @param awaitingClosure       RESPONDED — answered, not yet closed
 * @param overdue               past due and still DRAFT or SUBMITTED
 * @param withUnresolvedImpact  answered RFIs flagged as affecting schedule/cost that could not be closed yet
 * @param avgResponseDays       mean days from request to (latest) response, null if none answered
 * @param ageing                unanswered RFIs by days open: 0-7, 8-14, 15-30, 30+
 */
public record RfiSummaryResponse(
        UUID projectId, int total, Map<String, Integer> byStatus,
        int awaitingResponse, int awaitingClosure, int overdue, int withUnresolvedImpact,
        Double avgResponseDays, Map<String, Integer> ageing
) {}