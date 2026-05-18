package za.co.handyflow.platform.catalogue.dto;

import java.util.UUID;

public record CategoryResponse(
        UUID id,
        String name,
        String description,
        int sortOrder
) {}