package za.co.handyflow.platform.earthmoving.dto;
import jakarta.validation.constraints.NotBlank;
public record UpdateAssetStatusRequest(
        @NotBlank String status,
        String note
) {}