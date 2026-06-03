package za.co.handyflow.platform.accountant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

// Workpapers
public record CreateFolderRequest(
        @NotBlank String name,
        UUID parentId,
        @NotNull Integer engagementYear,
        String folderType
) {
}
