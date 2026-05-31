package za.co.handyflow.platform.tasks.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "task_time_logs")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class TaskTimeLog {

    @Id private UUID id = UUID.randomUUID();

    @Column(name = "task_id",   nullable = false) private UUID       taskId;
    @Column(name = "tenant_id", nullable = false) private UUID       tenantId;
    @Column(name = "user_id")                      private UUID       userId;
    @Column(name = "user_name", nullable = false)  private String     userName;
    @Column(nullable = false, precision = 6, scale = 2) private BigDecimal hours;
    private String    description;
    @Column(name = "logged_date") private LocalDate loggedDate;
    @Column(name = "created_at")  private Instant   createdAt;

    public static TaskTimeLog create(UUID taskId, UUID tenantId, UUID userId,
                                      String userName, BigDecimal hours,
                                      String description, LocalDate loggedDate) {
        TaskTimeLog l  = new TaskTimeLog();
        l.taskId       = taskId;
        l.tenantId     = tenantId;
        l.userId       = userId;
        l.userName     = userName;
        l.hours        = hours;
        l.description  = description;
        l.loggedDate   = loggedDate != null ? loggedDate : LocalDate.now();
        l.createdAt    = Instant.now();
        return l;
    }
}
