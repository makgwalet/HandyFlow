package za.co.handyflow.platform.facilities.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.UUID;

/** Issue parts to a work order; adds a PARTS cost line to it at the current average cost. */
public record IssueStockRequest(
        @NotNull UUID workOrderId, @NotNull @DecimalMin("0.001") BigDecimal quantity, @Size(max = 300) String note
) {}
