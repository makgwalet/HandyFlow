package za.co.handyflow.platform.tasks.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "task_boards")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class TaskBoard {

    @Id private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(nullable = false) private String  name;
    private String  description;
    private String  color;
    @Column(name = "is_default") private boolean isDefault  = false;
    @Column(nullable = false)    private boolean archived   = false;
    @Column(name = "created_by") private UUID    createdBy;
    @Column(name = "created_at") private Instant createdAt;
    @Column(name = "updated_at") private Instant updatedAt;

    public static TaskBoard create(TenantId tenantId, String name, String description,
                                    String color, boolean isDefault, UUID createdBy) {
        TaskBoard b   = new TaskBoard();
        b.tenantId    = tenantId;
        b.name        = name;
        b.description = description;
        b.color       = color;
        b.isDefault   = isDefault;
        b.createdBy   = createdBy;
        b.createdAt   = Instant.now();
        b.updatedAt   = Instant.now();
        return b;
    }

    public void update(String name, String description, String color) {
        if (name        != null) this.name        = name;
        if (description != null) this.description = description;
        if (color       != null) this.color       = color;
        this.updatedAt = Instant.now();
    }

    public void archive()   { this.archived = true;  this.updatedAt = Instant.now(); }
    public void unarchive() { this.archived = false; this.updatedAt = Instant.now(); }
}
