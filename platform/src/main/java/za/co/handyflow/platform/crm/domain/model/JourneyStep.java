package za.co.handyflow.platform.crm.domain.model;

import jakarta.persistence.*;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

/** One step of a campaign's journey: do this many days after the person was added. */
@Entity
@Table(name = "crm_journey_steps")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class JourneyStep {
    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "campaign_id", nullable = false)
    private UUID campaignId;

    @Column(name = "position", nullable = false)
    private int position;

    @Column(name = "day_offset", nullable = false)
    private int dayOffset;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 20)
    private WorkflowAction action;

    @Column(name = "step_text", length = 500)
    private String text;

    @Column(name = "action_user")
    private UUID actionUser;

    public static JourneyStep of(TenantId tenantId, UUID campaignId, int position, int dayOffset, WorkflowAction action, String text, UUID user) {
        var s = new JourneyStep();
        s.tenantId = tenantId;
        s.campaignId = campaignId;
        s.position = position;
        s.dayOffset = dayOffset;
        s.action = action;
        s.text = action == WorkflowAction.ASSIGN_OWNER || text == null ? null : text.strip();
        s.actionUser = action == WorkflowAction.ASSIGN_OWNER || action == WorkflowAction.CREATE_TASK ? user : null;
        return s;
    }
}
