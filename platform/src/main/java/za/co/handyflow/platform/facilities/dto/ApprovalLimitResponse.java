package za.co.handyflow.platform.facilities.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ApprovalLimitResponse(UUID userId, BigDecimal maxOrderValue, UUID setByUserId, Instant updatedAt) {}
