package za.co.handyflow.platform.earthmoving.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Whether the scheduled run is switched on, and the latest runs. */
public record BillingHistoryResponse(boolean autoBillingEnabled, List<Run> runs) {
    public record Run(UUID id, String month, String trigger, boolean draftsRequested, int chargesCreated, int alreadyBilled,
                      int exceptions, int invoicesCreated, UUID createdBy, Instant createdAt) {}
}
