package za.co.handyflow.platform.internalaudit.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

public record CreateWorkpaperFolderRequest(
        UUID parentId,
        @NotBlank String name,
        String folderType, // PLANNING | FIELDWORK | SAMPLING | FINDINGS | REPORTING | GENERAL
        int sortOrder
) {}
