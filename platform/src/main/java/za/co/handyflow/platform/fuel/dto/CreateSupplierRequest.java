package za.co.handyflow.platform.fuel.dto;
import jakarta.validation.constraints.NotBlank;
public record CreateSupplierRequest(
        @NotBlank String name, String contactName,
        String contactPhone, String contactEmail, String accountNumber
) {}