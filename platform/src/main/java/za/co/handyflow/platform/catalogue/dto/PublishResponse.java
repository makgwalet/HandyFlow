package za.co.handyflow.platform.catalogue.dto;

import java.util.List;
import java.util.UUID;

/** One row per item asked for. Outcomes: PUBLISHED, ALREADY, BLOCKED (with reasons), UNPUBLISHED, NOT_PUBLISHED; a dry run uses WOULD_PUBLISH. */
public record PublishResponse(UUID channelId, boolean dryRun, int done, int unchanged, int blocked, List<Row> rows) {
    public record Row(UUID itemId, String name, String sku, String outcome, List<String> problems) {}
}
