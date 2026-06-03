package za.co.handyflow.platform.accountant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record CreateDocumentRequestRequest(
        @NotBlank String description,
        @NotNull List<String> items,
        LocalDate dueDate,
        UUID folderId
) {
}
