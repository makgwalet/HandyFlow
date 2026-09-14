package za.co.handyflow.platform.internalaudit.dto;

import java.util.UUID;

public record WorkpaperFolderResponse(UUID id, String name, UUID parentId, String folderType, int sortOrder) {}
