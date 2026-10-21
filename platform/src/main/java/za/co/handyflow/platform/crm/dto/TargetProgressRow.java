package za.co.handyflow.platform.crm.dto;

import java.math.BigDecimal;
import java.util.UUID;

/** One salesperson in one month. target is null when none is set; percent is null without a target. */
public record TargetProgressRow(UUID ownerId, BigDecimal target, BigDecimal achieved, long wonDeals, BigDecimal percent) {}
