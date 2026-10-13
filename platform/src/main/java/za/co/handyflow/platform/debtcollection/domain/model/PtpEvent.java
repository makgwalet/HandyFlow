package za.co.handyflow.platform.debtcollection.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.AggregateRoot;
import za.co.handyflow.platform.shared.TenantId;

import java.util.UUID;

/** Append-only PTP audit entry (proposed, accepted, receipt, kept, broken, recovered, cancelled, series-next). actor null = system. */
@Entity
@Table(name = "debtcollection_ptp_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PtpEvent extends AggregateRoot<PtpEvent> {

    @Column(name = "ptp_id", nullable = false)
    private UUID ptpId;

    @Column(name = "case_id", nullable = false)
    private UUID caseId;

    @Column(name = "event_type", nullable = false, length = 40)
    private String eventType;

    @Column(columnDefinition = "TEXT")
    private String detail;

    @Column(name = "actor_user_id")
    private UUID actorUserId;

    @Column(name = "actor_user_name", length = 255)
    private String actorUserName;

    public static PtpEvent of(TenantId tenantId, UUID ptpId, UUID caseId, String eventType, String detail,
                              UUID actorUserId, String actorUserName) {
        PtpEvent e = new PtpEvent();
        e.initTenantId(tenantId);
        e.ptpId = ptpId;
        e.caseId = caseId;
        e.eventType = eventType;
        e.detail = detail;
        e.actorUserId = actorUserId;
        e.actorUserName = actorUserName;
        return e;
    }
}
