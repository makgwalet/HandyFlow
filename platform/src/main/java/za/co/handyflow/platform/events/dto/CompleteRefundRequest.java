package za.co.handyflow.platform.events.dto;

import jakarta.validation.constraints.Size;

public record CompleteRefundRequest(
        @Size(max = 100) String reference   // bank / payout reference
) {}