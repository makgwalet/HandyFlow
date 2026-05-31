package za.co.handyflow.platform.admin.dto;
import jakarta.validation.constraints.NotBlank;
public record AdminTotpConfirmRequest(@NotBlank String code) {}