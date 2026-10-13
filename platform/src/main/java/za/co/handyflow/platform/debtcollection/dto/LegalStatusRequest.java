package za.co.handyflow.platform.debtcollection.dto;

import jakarta.validation.constraints.NotBlank;

public record LegalStatusRequest(@NotBlank String legalStatus, String detail) {}
