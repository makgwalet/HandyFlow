package za.co.handyflow.platform.warehousing.dto;

import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;

/** Shared shape for create and update — a location has no update-only fields (unlike WhseClient's notes). */
public record UpsertLocationRequest(@NotBlank String code, String zone, String description, BigDecimal capacityUnits) {}
