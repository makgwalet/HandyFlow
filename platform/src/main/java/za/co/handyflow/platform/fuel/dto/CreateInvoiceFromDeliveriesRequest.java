package za.co.handyflow.platform.fuel.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.UUID;

public record CreateInvoiceFromDeliveriesRequest(@NotEmpty(message = "Choose at least one delivery") List<UUID> deliveryIds) {}
