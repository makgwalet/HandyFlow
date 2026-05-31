package za.co.handyflow.platform.tasks.dto;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
public record BoardResponse(
        UUID   id, String name, String description, String color,
        boolean isDefault, boolean archived,
        List<ColumnResponse> columns,
        Instant createdAt
) {}
