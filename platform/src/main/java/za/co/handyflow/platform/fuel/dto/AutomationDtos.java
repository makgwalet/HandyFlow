package za.co.handyflow.platform.fuel.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Phase 6b: automation rules and what they did. */
public final class AutomationDtos {
    private AutomationDtos() {}

    /** tankId is used only when adding; a rule's tank cannot change (add a new rule instead). enabled null means "on" when adding and "leave as is" when editing. */
    public record RuleRequest(UUID tankId, UUID supplierId, @NotNull @Positive BigDecimal reorderBelowLitres, @NotNull @Positive BigDecimal orderLitres,
                              @PositiveOrZero BigDecimal estimatedPrice, Boolean enabled) {}

    public record RuleView(UUID id, UUID tankId, String tankName, String fuelType, BigDecimal currentLitres, UUID supplierId, String supplierName,
                           BigDecimal reorderBelowLitres, BigDecimal orderLitres, BigDecimal estimatedPrice, boolean enabled, boolean belowLevel,
                           Instant lastCheckedAt, Instant lastDraftedAt) {}

    public record LogView(UUID id, UUID ruleId, String tankName, String outcome, String detail, UUID requisitionId, Instant ranAt) {}

    /** drafted: new draft requisitions; alreadyOpen: below level but a requisition or order for the tank is already on the way. */
    public record RunResult(int checked, int drafted, int alreadyOpen, int failed, List<String> requisitionNumbers) {}

    /** varianceReviewDays: days an unresolved delivery variance may stay open before the administrators are told; null means no alert. */
    public record AutomationSettings(boolean alertOverduePurchaseOrders, Integer varianceReviewDays) {}
}
