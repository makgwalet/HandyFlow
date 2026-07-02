package za.co.handyflow.platform.projects.dto;

import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.UUID;

// ── Create ────────────────────────────────────────────────────────────────────
public record CreateRfiRequest(

        @NotBlank(message = "RFI title is required")
        @Size(max = 500, message = "Title must not exceed 500 characters")
        String title,

        @Size(max = 5000, message = "Description must not exceed 5 000 characters")
        String description,

        // DESIGN | SITE | MATERIALS | SAFETY | SPECIFICATION | OTHER
        String category,

        @Size(max = 255, message = "Requested-by name must not exceed 255 characters")
        String requestedBy,

        UUID requestedById,

        LocalDate requestedDate,

        LocalDate dueDate,

        /** If true, immediately transitions status to SUBMITTED after creation. */
        boolean submitImmediately

) {}
