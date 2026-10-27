package za.co.handyflow.platform.facilitiesmanagement.dto;

import jakarta.validation.constraints.NotBlank;

/** Used to reject a request and to send completed work back for rework. */
public record FmReasonRequest(@NotBlank String reason) {}