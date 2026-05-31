package za.co.handyflow.platform.desk.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateCategoryRequest(
        @NotBlank String name,
                  String description,
                  String color,
                  int    sortOrder
) {}
