package za.co.handyflow.platform.hr.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "hr_leave_settings")
@Getter
@NoArgsConstructor
public class HrLeaveSettings {

    @Id @Column(name = "tenant_id") UUID tenantId;
    @Column(name = "ledger_start_date") LocalDate ledgerStartDate;
    @Column(name = "created_at") Instant createdAt;

    public static HrLeaveSettings startingOn(UUID tenantId, LocalDate start) {
        HrLeaveSettings s = new HrLeaveSettings();
        s.tenantId = tenantId;
        s.ledgerStartDate = start;
        s.createdAt = Instant.now();
        return s;
    }
}
