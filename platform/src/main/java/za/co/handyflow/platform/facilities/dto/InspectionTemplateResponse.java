package za.co.handyflow.platform.facilities.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record InspectionTemplateResponse(
        UUID id, String name, String description, String assetCategory, boolean active, int revision,
        List<Item> items, Instant createdAt, Instant updatedAt
) {
    public record Item(UUID id, int position, String prompt, boolean critical) {}
}
