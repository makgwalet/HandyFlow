package za.co.handyflow.platform.facilities.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record CompleteWorkOrderRequest(String completionNotes, BigDecimal cost, Instant completedAt) {}
