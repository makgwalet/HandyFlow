package za.co.handyflow.platform.desk.dto;

import java.util.UUID;

public record DeskCategoryResponse(
        UUID   id,
        String name,
        String description,
        String color,
        int    sortOrder
) {}
