package za.co.handyflow.platform.agriculture.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.Getter;

import java.util.UUID;

/** Who added a record and who last changed it. Filled by {@link AgAuditListener}; null for records that predate it. */
@Embeddable
@Getter
public class AgAuditStamp {

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "created_by_name", length = 255)
    private String createdByName;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @Column(name = "updated_by_name", length = 255)
    private String updatedByName;

    public void recordCreated(UUID userId, String name) {
        this.createdBy = userId;
        this.createdByName = name;
        recordUpdated(userId, name);
    }

    public void recordUpdated(UUID userId, String name) {
        this.updatedBy = userId;
        this.updatedByName = name;
    }
}
