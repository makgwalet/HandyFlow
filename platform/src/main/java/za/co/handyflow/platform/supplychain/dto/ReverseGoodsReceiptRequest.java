package za.co.handyflow.platform.supplychain.dto;

import jakarta.validation.constraints.NotBlank;

public record ReverseGoodsReceiptRequest(@NotBlank String reason) {}
