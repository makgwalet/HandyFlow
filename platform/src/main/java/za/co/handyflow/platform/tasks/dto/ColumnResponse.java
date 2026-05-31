package za.co.handyflow.platform.tasks.dto;
import java.util.List;
import java.util.UUID;
public record ColumnResponse(
        UUID   id, String name, String color, int sortOrder,
        boolean isDoneColumn,
        List<TaskResponse> tasks   // null on board list, populated on board detail
) {}