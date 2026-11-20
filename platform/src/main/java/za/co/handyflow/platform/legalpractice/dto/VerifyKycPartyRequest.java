package za.co.handyflow.platform.legalpractice.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record VerifyKycPartyRequest(@NotNull LocalDate verifiedOn) {
}
