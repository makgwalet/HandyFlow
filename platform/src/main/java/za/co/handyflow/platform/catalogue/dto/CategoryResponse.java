package za.co.handyflow.platform.catalogue.dto;

import java.util.UUID;

/** itemCount is the number of live items in the category; null where it was not worked out. */
public record CategoryResponse(UUID id, String name, String description, Integer sortOrder, Long itemCount) {
    public CategoryResponse(UUID id, String name, String description, Integer sortOrder) {
        this(id, name, description, sortOrder, null);
    }
}
