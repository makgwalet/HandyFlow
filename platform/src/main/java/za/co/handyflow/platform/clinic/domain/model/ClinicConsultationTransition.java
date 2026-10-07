package za.co.handyflow.platform.clinic.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/** One audited move of a consultation through the nurse/doctor handoff (DEC-CLINIC-004). */
@Entity
@Table(name = "clinic_consultation_transitions")
@Getter
@NoArgsConstructor
public class ClinicConsultationTransition {

    @Id UUID id;
    @Column(name = "tenant_id")       UUID    tenantId;
    @Column(name = "consultation_id") UUID    consultationId;
    @Column(name = "from_status")     String  fromStatus;
    @Column(name = "to_status")       String  toStatus;
    @Column(name = "actor_user_id")   UUID    actorUserId;
    @Column(name = "reason_code")     String  reasonCode;
    String comment;
    @Column(name = "created_at")      Instant createdAt;

    public static ClinicConsultationTransition of(ClinicConsultation c, String from, String to,
                                                  UUID actor, String reasonCode, String comment) {
        ClinicConsultationTransition t = new ClinicConsultationTransition();
        t.id             = UUID.randomUUID();
        t.tenantId       = c.getTenantId();
        t.consultationId = c.getId();
        t.fromStatus     = from;
        t.toStatus       = to;
        t.actorUserId    = actor;
        t.reasonCode     = reasonCode;
        t.comment        = comment;
        t.createdAt      = Instant.now();
        return t;
    }
}
