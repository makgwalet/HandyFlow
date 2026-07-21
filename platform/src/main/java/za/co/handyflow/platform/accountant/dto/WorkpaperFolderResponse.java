package za.co.handyflow.platform.accountant.dto;

import java.util.UUID;

public record WorkpaperFolderResponse(
        UUID id,
        String name,
        UUID parentId,
        int engagementYear,
        String folderType,
        int sortOrder
) {
}