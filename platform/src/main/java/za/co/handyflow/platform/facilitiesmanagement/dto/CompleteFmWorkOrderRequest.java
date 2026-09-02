package za.co.handyflow.platform.facilitiesmanagement.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record CompleteFmWorkOrderRequest(String completionNotes, BigDecimal cost, Instant completedAt) {}
