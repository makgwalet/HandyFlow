package za.co.handyflow.platform.facilities.dto;

import jakarta.validation.constraints.NotBlank;

/** Used to reject a request and to send completed work back for rework. */
public record ReasonRequest(@NotBlank String reason) {}