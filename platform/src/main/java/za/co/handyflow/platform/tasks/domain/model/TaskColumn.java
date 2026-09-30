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

    /** Workflow stage of this column. Drives the status of every task in it (see V304). */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TaskCategory category = TaskCategory.TODO;

    public static TaskColumn create(UUID boardId, UUID tenantId, String name,
                                     String color, int sortOrder, boolean isDoneColumn) {
        TaskColumn c  = new TaskColumn();
        c.boardId     = boardId;
        c.tenantId    = tenantId;
        c.name        = name;
        c.color       = color;
        c.sortOrder   = sortOrder;
        c.isDoneColumn = isDoneColumn;
        c.category    = isDoneColumn ? TaskCategory.DONE : TaskCategory.TODO;
        return c;
    }

    /** Same, with an explicit category (null keeps the default derived from isDoneColumn). */
    public static TaskColumn create(UUID boardId, UUID tenantId, String name,
                                     String color, int sortOrder, boolean isDoneColumn,
                                     TaskCategory category) {
        TaskColumn c = create(boardId, tenantId, name, color, sortOrder, isDoneColumn);
        c.applyCategory(category);
        return c;
    }

    public void update(String name, String color, int sortOrder, boolean isDoneColumn) {
        if (name  != null) this.name  = name;
        if (color != null) this.color = color;
        this.sortOrder    = sortOrder;
        this.isDoneColumn = isDoneColumn;
        // keep the category consistent with the done flag when only the flag is sent
        if (isDoneColumn) this.category = TaskCategory.DONE;
        else if (this.category == TaskCategory.DONE) this.category = TaskCategory.TODO;
    }

    /** Sets the category (and with it the done flag). A null category changes nothing. */
    public void applyCategory(TaskCategory category) {
        if (category == null) return;
        this.category     = category;
        this.isDoneColumn = category == TaskCategory.DONE;
    }
}
