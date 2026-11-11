package za.co.handyflow.platform.warehousing.dto;

import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;
import java.util.UUID;

/** Shared shape for create and update — a location has no update-only fields (unlike WhseClient's notes). warehouseId is read on create only (empty = the default warehouse). */
public record UpsertLocationRequest(@NotBlank String code, String zone, String description, BigDecimal capacityUnits, UUID warehouseId) {}
