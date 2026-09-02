package za.co.handyflow.platform.warehousing.dto;

import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;

/** sku is intentionally not editable here — WhseItem.update() doesn't accept it either, same "the SKU is the item's identity" convention as most catalogue entities in this codebase. */
public record UpdateItemRequest(@NotBlank String description, String uom, BigDecimal storageRatePerUnitPerMonth) {}
