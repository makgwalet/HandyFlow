package za.co.handyflow.platform.agriculture.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

/** One change of an animal's tag number (a temporary tag replaced by the permanent one). Append-only. */
@Entity
@Table(name = "ag_animal_tag_history")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AgAnimalTagHistory {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "animal_id", nullable = false)
    private UUID animalId;

    @Column(name = "old_tag", nullable = false)
    private String oldTag;

    @Column(name = "new_tag", nullable = false)
    private String newTag;

    @Column(name = "changed_by")
    private UUID changedBy;

    @Column(name = "changed_by_name")
    private String changedByName;

    @Column(name = "changed_at", nullable = false, updatable = false)
    private Instant changedAt;

    public static AgAnimalTagHistory create(TenantId tenantId, UUID animalId, String oldTag, String newTag,
                                            UUID changedBy, String changedByName) {
        if (tenantId == null) throw new IllegalArgumentException("tenantId is required");
        if (animalId == null) throw new IllegalArgumentException("animalId is required");
        if (oldTag == null || oldTag.isBlank() || newTag == null || newTag.isBlank()) throw new IllegalArgumentException("both tags are required");
        if (oldTag.equals(newTag)) throw new IllegalArgumentException("the tag did not change");
        AgAnimalTagHistory h = new AgAnimalTagHistory();
        h.tenantId = tenantId;
        h.animalId = animalId;
        h.oldTag = oldTag;
        h.newTag = newTag;
        h.changedBy = changedBy;
        h.changedByName = changedByName;
        h.changedAt = Instant.now();
        return h;
    }
}
