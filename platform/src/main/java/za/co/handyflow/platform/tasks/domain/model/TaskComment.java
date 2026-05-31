package za.co.handyflow.platform.tasks.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

// ── TaskComment ───────────────────────────────────────────────────────────────
@Entity
@Table(name = "task_comments")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class TaskComment {

    @Id private UUID id = UUID.randomUUID();

    @Column(name = "task_id",    nullable = false) private UUID    taskId;
    @Column(name = "tenant_id",  nullable = false) private UUID    tenantId;
    @Column(name = "author_id")                    private UUID    authorId;
    @Column(name = "author_name",nullable = false) private String  authorName;
    @Column(nullable = false)                       private String  body;
    @Column(name = "created_at") private Instant createdAt;
    @Column(name = "updated_at") private Instant updatedAt;

    public static TaskComment create(UUID taskId, UUID tenantId, UUID authorId,
                                      String authorName, String body) {
        TaskComment c  = new TaskComment();
        c.taskId       = taskId;
        c.tenantId     = tenantId;
        c.authorId     = authorId;
        c.authorName   = authorName;
        c.body         = body;
        c.createdAt    = Instant.now();
        c.updatedAt    = Instant.now();
        return c;
    }
}
