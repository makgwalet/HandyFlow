package za.co.handyflow.platform.property.dto;
import jakarta.validation.constraints.NotBlank; import jakarta.validation.constraints.NotNull;
import java.time.Instant; import java.util.List; import java.util.Map; import java.util.UUID;
public record CreateInspectionRequest(
        @NotBlank String type, @NotNull Instant inspectedAt,
        String inspectedBy, String overallCondition, String notes,
        UUID leaseId, List<Map<String, String>> items
) {}