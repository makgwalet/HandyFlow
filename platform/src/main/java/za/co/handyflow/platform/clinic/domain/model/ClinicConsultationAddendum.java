package za.co.handyflow.platform.clinic.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/** A dated note added to a signed or locked consultation. Never edited or deleted. */
@Entity
@Table(name = "clinic_consultation_addenda")
@Getter
@NoArgsConstructor
public class ClinicConsultationAddendum {

    @Id UUID id;
    @Column(name = "tenant_id")       UUID    tenantId;
    @Column(name = "consultation_id") UUID    consultationId;
    @Column(name = "author_user_id")  UUID    authorUserId;
    String text;
    @Column(name = "created_at")      Instant createdAt;

    public static ClinicConsultationAddendum of(ClinicConsultation c, UUID authorUserId, String text) {
        ClinicConsultationAddendum a = new ClinicConsultationAddendum();
        a.id             = UUID.randomUUID();
        a.tenantId       = c.getTenantId();
        a.consultationId = c.getId();
        a.authorUserId   = authorUserId;
        a.text           = text;
        a.createdAt      = Instant.now();
        return a;
    }
}
