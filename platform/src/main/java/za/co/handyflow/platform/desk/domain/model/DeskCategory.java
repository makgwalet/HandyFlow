package za.co.handyflow.platform.desk.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "desk_categories")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class DeskCategory {

    @Id private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(nullable = false) private String  name;
    private String  description;
    private String  color;
    @Column(name = "sort_order") private int  sortOrder = 0;
    @Column(nullable = false)    private boolean active = true;
    @Column(name = "created_at") private Instant createdAt;

    public static DeskCategory create(TenantId tenantId, String name,
                                       String description, String color, int sortOrder) {
        DeskCategory c = new DeskCategory();
        c.tenantId     = tenantId;
        c.name         = name;
        c.description  = description;
        c.color        = color;
        c.sortOrder    = sortOrder;
        c.active       = true;
        c.createdAt    = Instant.now();
        return c;
    }
}
