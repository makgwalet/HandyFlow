package za.co.handyflow.platform.security.dto;
import java.math.BigDecimal; import java.time.Instant;
import java.util.List; import java.util.Map; import java.util.UUID;
public record SiteResponse(
        UUID    id,
        String  name,
        UUID    customerId,
        Object  address,
        BigDecimal  latitude,
        BigDecimal  longitude,
        String  contactName,
        String  contactPhone,
        boolean active,
        List<CheckpointResponse> checkpoints,
        String  contractStatus,      // ← new
        java.time.LocalDate contractStart,  // ← new
        java.time.LocalDate contractEnd,    // ← new
        String  terminationReason,   // ← new
        Instant createdAt
) {}