package za.co.handyflow.platform.crm.domain.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

/** A named outreach with a goal and a list of people. Its journey (timed steps) runs for each person while the campaign is active. */
@Entity
@Table(name = "crm_campaigns")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Campaign {
    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "goal", length = 500)
    private String goal;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 12)
    private CampaignStatus status = CampaignStatus.DRAFT;

    @Column(name = "starts_on")
    private LocalDate startsOn;

    @Column(name = "ends_on")
    private LocalDate endsOn;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    public static Campaign create(TenantId tenantId, String name, String goal, LocalDate startsOn, LocalDate endsOn, UUID createdBy) {
        var c = new Campaign();
        c.tenantId = tenantId;
        c.createdBy = createdBy;
        c.createdAt = Instant.now();
        c.change(name, goal, startsOn, endsOn);
        return c;
    }

    public void change(String name, String goal, LocalDate startsOn, LocalDate endsOn) {
        this.name = name.strip();
        this.goal = goal == null || goal.isBlank() ? null : goal.strip();
        this.startsOn = startsOn;
        this.endsOn = endsOn;
        this.updatedAt = Instant.now();
    }

    public void moveTo(CampaignStatus status) { this.status = status; this.updatedAt = Instant.now(); }

    public void softDelete() { this.deletedAt = Instant.now(); this.updatedAt = this.deletedAt; }
}
