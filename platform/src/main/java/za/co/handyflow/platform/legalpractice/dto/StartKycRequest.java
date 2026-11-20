package za.co.handyflow.platform.legalpractice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** subjectKind: INDIVIDUAL, COMPANY, TRUST or PARTNERSHIP. */
public record StartKycRequest(@NotNull UUID clientId, @NotBlank String subjectKind) {
}
