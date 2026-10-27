package za.co.handyflow.platform.facilities.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record ApprovalLimitRequest(@NotNull @DecimalMin("0") BigDecimal maxOrderValue) {}
