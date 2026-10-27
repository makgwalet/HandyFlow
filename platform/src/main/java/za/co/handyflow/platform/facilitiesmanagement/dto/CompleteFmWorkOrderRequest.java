package za.co.handyflow.platform.facilitiesmanagement.dto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * {@code cost} is used only when the work order has no itemised cost lines; if it has any, the sum of the
 * BILLABLE lines wins. {@code downtimeMinutes} is how long the asset/area was out of service;
 * {@code failureCause} records why it failed. Both optional.
 */
public record CompleteFmWorkOrderRequest(
        String completionNotes, BigDecimal cost, Instant completedAt,
        Integer downtimeMinutes, String failureCause
) {}