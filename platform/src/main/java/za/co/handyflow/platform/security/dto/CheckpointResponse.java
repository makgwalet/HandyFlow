package za.co.handyflow.platform.security.dto;
import java.util.UUID;
public record CheckpointResponse(
        UUID id, String name, String description, String qrCode, int sortOrder
) {}