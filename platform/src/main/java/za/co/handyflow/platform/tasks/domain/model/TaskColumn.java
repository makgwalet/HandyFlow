package za.co.handyflow.platform.tasks.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(name = "task_columns")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class TaskColumn {

    @Id private UUID id = UUID.randomUUID();

    @Column(name = "board_id",  nullable = false) private UUID    boardId;
    @Column(name = "tenant_id", nullable = false) private UUID    tenantId;
    @Column(nullable = false)                      private String  name;
    private String  color;
    @Column(name = "sort_order")    private int     sortOrder   = 0;
    @Column(name = "is_done_column") private boolean isDoneColumn = false;

    public static TaskColumn create(UUID boardId, UUID tenantId, String name,
                                     String color, int sortOrder, boolean isDoneColumn) {
        TaskColumn c  = new TaskColumn();
        c.boardId     = boardId;
        c.tenantId    = tenantId;
        c.name        = name;
        c.color       = color;
        c.sortOrder   = sortOrder;
        c.isDoneColumn = isDoneColumn;
        return c;
    }

    public void update(String name, String color, int sortOrder, boolean isDoneColumn) {
        if (name  != null) this.name  = name;
        if (color != null) this.color = color;
        this.sortOrder    = sortOrder;
        this.isDoneColumn = isDoneColumn;
    }
}
