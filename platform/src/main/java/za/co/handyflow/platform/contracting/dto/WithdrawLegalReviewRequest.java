package za.co.handyflow.platform.contracting.dto;

import jakarta.validation.constraints.NotBlank;

public record WithdrawLegalReviewRequest(@NotBlank String reason) {}
