package za.co.handyflow.platform.tasks.dto;

import java.util.List;
import java.util.UUID;

public record ColumnResponse(
        UUID   id, String name, String color, int sortOrder,
        boolean isDoneColumn,
        String category,           // TODO | IN_PROGRESS | IN_REVIEW | BLOCKED | DONE
        List<TaskResponse> tasks   // null on board list, populated on board detail
) {}
