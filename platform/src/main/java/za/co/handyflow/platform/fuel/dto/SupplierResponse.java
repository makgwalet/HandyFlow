package za.co.handyflow.platform.fuel.dto;
import java.time.Instant; import java.util.UUID;
public record SupplierResponse(
        UUID id, String name, String contactName,
        String contactPhone, String contactEmail,
        String accountNumber, Instant createdAt
) {}