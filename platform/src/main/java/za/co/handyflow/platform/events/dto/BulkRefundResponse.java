package za.co.handyflow.platform.events.dto;

import java.util.List;

public record BulkRefundResponse(
        int requested,                  // refunds successfully raised
        int failed,                     // payments that could not be refunded (see failures)
        boolean moreRemaining,          // true if the per-call limit was hit — call again for the rest
        List<RefundResponse> refunds,
        List<String> failures
) {}