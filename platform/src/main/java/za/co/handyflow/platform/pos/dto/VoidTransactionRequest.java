package za.co.handyflow.platform.pos.dto;
import jakarta.validation.constraints.NotBlank;
public record VoidTransactionRequest(@NotBlank String reason) {}