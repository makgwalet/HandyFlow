package za.co.handyflow.platform.catalogue.dto;

import java.util.List;
import java.util.UUID;

/** Outcome per item: OK (done, or would be done on a dry run), UNCHANGED, or REFUSED with the reason. */
public record BulkResponse(boolean dryRun, int ok, int unchanged, int refused, List<Row> rows) {
    public record Row(UUID id, String name, String sku, String outcome, String message) {}
}
