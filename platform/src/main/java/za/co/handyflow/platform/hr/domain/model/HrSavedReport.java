package za.co.handyflow.platform.hr.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** A report view one person keeps: settings only, never figures. */
@Entity
@Table(name = "hr_saved_reports")
@Getter
@NoArgsConstructor
public class HrSavedReport {

    @Id UUID id;
    @Column(name = "tenant_id")        UUID tenantId;
    @Column(name = "owner_id")         UUID ownerId;
    String name;
    String kind;
    String period;
    String schedule;
    @Column(name = "run_day")          Integer runDay;
    @Column(name = "last_notified_on") LocalDate lastNotifiedOn;
    @Column(name = "created_at")       Instant createdAt;

    public static HrSavedReport of(UUID tenantId, UUID ownerId, String name, String kind, String period, String schedule, Integer runDay) {
        HrSavedReport r = new HrSavedReport();
        r.id = UUID.randomUUID();
        r.tenantId = tenantId;
        r.ownerId = ownerId;
        r.name = name.trim();
        r.kind = kind;
        r.period = period;
        r.schedule = schedule == null ? SavedReportRules.NONE : schedule;
        r.runDay = SavedReportRules.NONE.equals(r.schedule) ? null : runDay;
        r.createdAt = Instant.now();
        return r;
    }

    public void notified(LocalDate on) { this.lastNotifiedOn = on; }
}
