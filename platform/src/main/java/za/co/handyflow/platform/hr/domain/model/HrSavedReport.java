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
    @Column(name = "email_reminder")   boolean emailReminder;
    @Column(name = "created_at")       Instant createdAt;

    public static HrSavedReport of(UUID tenantId, UUID ownerId, String name, String kind, String period, String schedule, Integer runDay) {
        return of(tenantId, ownerId, name, kind, period, schedule, runDay, false);
    }

    public static HrSavedReport of(UUID tenantId, UUID ownerId, String name, String kind, String period, String schedule, Integer runDay, boolean emailReminder) {
        HrSavedReport r = new HrSavedReport();
        r.id = UUID.randomUUID();
        r.tenantId = tenantId;
        r.ownerId = ownerId;
        r.name = name.trim();
        r.kind = kind;
        r.period = period;
        r.schedule = schedule == null ? SavedReportRules.NONE : schedule;
        r.runDay = SavedReportRules.NONE.equals(r.schedule) ? null : runDay;
        r.emailReminder = emailReminder && !SavedReportRules.NONE.equals(r.schedule);
        r.createdAt = Instant.now();
        return r;
    }

    /** Changes the settings. A changed reminder day starts again, so a new day is not held back by an old reminder. */
    public void edit(String name, String kind, String period, String schedule, Integer runDay, boolean emailReminder) {
        String sched = schedule == null ? SavedReportRules.NONE : schedule;
        boolean reminderChanged = !sched.equals(this.schedule) || !java.util.Objects.equals(this.runDay, SavedReportRules.NONE.equals(sched) ? null : runDay);
        this.name = name.trim();
        this.kind = kind;
        this.period = period;
        this.schedule = sched;
        this.runDay = SavedReportRules.NONE.equals(sched) ? null : runDay;
        this.emailReminder = emailReminder && !SavedReportRules.NONE.equals(sched);
        if (reminderChanged) this.lastNotifiedOn = null;
    }

    public void notified(LocalDate on) { this.lastNotifiedOn = on; }
}
