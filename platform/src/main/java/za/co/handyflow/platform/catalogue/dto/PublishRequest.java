package za.co.handyflow.platform.catalogue.dto;

import java.util.List;
import java.util.UUID;

/** Up to 200 items. {@code dryRun} shows what would happen and changes nothing. */
public record PublishRequest(List<UUID> itemIds, boolean dryRun) {}
