package za.co.handyflow.platform.catalogue.dto;

import java.util.UUID;

/**
 * itemCount is the number of live items directly in the category; null where it was not worked out.
 * path is the full name from the top ("Building › Cement"); depth is 1 for a top-level category.
 */
public record CategoryResponse(UUID id, String name, String description, Integer sortOrder, Long itemCount,
                               UUID parentId, String path, Integer depth) {
    public CategoryResponse(UUID id, String name, String description, Integer sortOrder) {
        this(id, name, description, sortOrder, null, null, name, 1);
    }
}
