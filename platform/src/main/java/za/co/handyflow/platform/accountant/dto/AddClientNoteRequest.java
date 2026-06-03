package za.co.handyflow.platform.accountant.dto;

import jakarta.validation.constraints.NotBlank;

public record AddClientNoteRequest(
        @NotBlank String note,
        boolean pinned
) {
}
