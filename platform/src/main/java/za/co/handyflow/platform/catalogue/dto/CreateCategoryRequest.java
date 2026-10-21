package za.co.handyflow.platform.catalogue.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/** parentId null makes a top-level category. On edit, a different parentId moves the category (with everything below it). */
public record CreateCategoryRequest(
        @NotBlank(message = "Category name is required")
        @Size(max = 100)
        String name,
        String description,
        UUID parentId
) {
    public CreateCategoryRequest(String name, String description) { this(name, description, null); }
}
