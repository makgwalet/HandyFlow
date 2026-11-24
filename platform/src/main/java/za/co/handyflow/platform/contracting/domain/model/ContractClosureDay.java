package za.co.handyflow.platform.contracting.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** A day this organisation is closed on top of the public holidays. The approval waiting clock does not run on it. */
@Entity
@Table(name = "contract_closure_days")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ContractClosureDay {
    @Id private UUID id;
    @Column(name = "tenant_id") private UUID tenantId;
    @Column(name = "closed_on") private LocalDate closedOn;
    private String reason;
    @Column(name = "created_by") private UUID createdBy;
    @Column(name = "created_at") private Instant createdAt;

    public static ContractClosureDay create(UUID tenantId, LocalDate day, String reason, UUID by) {
        if (day == null) throw new IllegalArgumentException("Choose the day");
        ContractClosureDay c = new ContractClosureDay();
        c.id = UUID.randomUUID(); c.tenantId = tenantId; c.closedOn = day;
        c.reason = reason == null || reason.isBlank() ? null : reason.trim().length() > 200 ? reason.trim().substring(0, 200) : reason.trim();
        c.createdBy = by; c.createdAt = Instant.now();
        return c;
    }
}
