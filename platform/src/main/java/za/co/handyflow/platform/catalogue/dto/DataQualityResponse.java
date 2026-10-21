package za.co.handyflow.platform.catalogue.dto;

import java.util.List;
import java.util.UUID;

/** Findings grouped by kind, biggest groups first. {@code items} holds at most 100 per group; {@code count} is the true number. */
public record DataQualityResponse(int scanned, int total, List<Group> groups) {
    public record Group(String kind, String label, String advice, int count, List<Finding> items) {}
    public record Finding(UUID itemId, String name, String sku, String detail) {}
}
