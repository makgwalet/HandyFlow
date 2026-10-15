package za.co.handyflow.platform.earthmoving.dto;
import jakarta.validation.constraints.NotBlank;
public record UpdateAssetStatusRequest(
        @NotBlank String status,
        String note,
        String overrideReason
) {
    public UpdateAssetStatusRequest(String status, String note) { this(status, note, null); }
}