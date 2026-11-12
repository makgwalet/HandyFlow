package za.co.handyflow.platform.fuel.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Phase 2 wholesale controls: supplier approval, purchase-order approval, large stock adjustments. */
public final class FuelWholesaleDtos {
    private FuelWholesaleDtos() {}

    /** cashVarianceTolerance and requireSeparateCloser: null means "leave as it is". */
    public record ControlsRequest(boolean requireSupplierApproval, BigDecimal adjustmentThresholdLitres, BigDecimal varianceTolerancePct,
                                  BigDecimal cashVarianceTolerance, Boolean requireSeparateCloser,
                                  BigDecimal priceChangeApprovalPct, BigDecimal minMarginPct) {
        public ControlsRequest(boolean requireSupplierApproval, BigDecimal adjustmentThresholdLitres, BigDecimal varianceTolerancePct,
                               BigDecimal cashVarianceTolerance, Boolean requireSeparateCloser) {
            this(requireSupplierApproval, adjustmentThresholdLitres, varianceTolerancePct, cashVarianceTolerance, requireSeparateCloser, null, null);
        }
        public ControlsRequest(boolean requireSupplierApproval, BigDecimal adjustmentThresholdLitres, BigDecimal varianceTolerancePct) {
            this(requireSupplierApproval, adjustmentThresholdLitres, varianceTolerancePct, null, null, null, null);
        }
    }
    public record ControlsResponse(boolean requireSupplierApproval, BigDecimal adjustmentThresholdLitres, BigDecimal varianceTolerancePct,
                                   BigDecimal cashVarianceTolerance, boolean requireSeparateCloser,
                                   BigDecimal priceChangeApprovalPct, BigDecimal minMarginPct) {}

    public record ReasonRequest(@NotBlank @Size(min = 5, max = 300, message = "Reason must be 5 to 300 characters") String reason) {}

    public record AdjustmentRequestView(UUID id, UUID tankId, String tankName, BigDecimal litres, String reason, String status, UUID requestedBy,
                                        Instant requestedAt, UUID decidedBy, Instant decidedAt, String decisionNote) {}

    /** Either the stock moved now (movement set) or the adjustment is waiting for a second person (request set). */
    public record AdjustOutcome(boolean pendingApproval, MovementResponse movement, AdjustmentRequestView request) {}
}
