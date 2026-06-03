package za.co.handyflow.platform.property.dto;
import jakarta.validation.constraints.NotBlank; import jakarta.validation.constraints.NotNull;
import java.time.Instant; import java.util.List; import java.util.Map; import java.util.UUID;
public record CreateInspectionRequest(
        UUID                       leaseId,
        @NotBlank String           type,            // MOVE_IN, MOVE_OUT, ROUTINE, MAINTENANCE
        @NotNull  Instant          inspectedAt,
        String                     inspectedBy,
        String                     overallCondition,// EXCELLENT, GOOD, FAIR, POOR
        String                     notes,
        List<Map<String, String>>  items,           // [{room, condition, notes}, ...]
        List<String>               photoUrls
) {}