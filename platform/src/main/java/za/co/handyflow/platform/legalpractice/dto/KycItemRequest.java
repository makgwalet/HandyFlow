package za.co.handyflow.platform.legalpractice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

public record KycItemRequest(@NotBlank String kind, UUID partyId, @NotBlank String description, String documentRef, UUID evidenceId, @NotNull LocalDate verifiedOn) {
}
