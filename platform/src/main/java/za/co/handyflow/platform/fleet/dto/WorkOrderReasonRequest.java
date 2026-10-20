package za.co.handyflow.platform.fleet.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Used to reject a quotation, put an order on hold, or cancel it — each needs a reason on record. */
public record WorkOrderReasonRequest(
        @NotBlank @Size(max = 4000) String reason
) {}