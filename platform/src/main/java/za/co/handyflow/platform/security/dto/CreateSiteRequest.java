package za.co.handyflow.platform.security.dto;
import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal; import java.util.Map; import java.util.UUID;
public record CreateSiteRequest(
        @NotBlank String name, UUID customerId,
        Map<String, String> address,
        BigDecimal latitude, BigDecimal longitude,
        String contactName, String contactPhone, String instructions
) {}
