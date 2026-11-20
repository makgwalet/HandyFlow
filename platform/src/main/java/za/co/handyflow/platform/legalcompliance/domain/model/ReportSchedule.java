package za.co.handyflow.platform.legalcompliance.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.legalcompliance.application.internal.ScheduleRules;
import za.co.handyflow.platform.legalcompliance.application.internal.ScheduleRules.Frequency;
import za.co.handyflow.platform.shared.AggregateRoot;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Tells chosen users, on a schedule, that a standard report is ready. The report itself is never sent. */
@Entity(name = "LcReportSchedule")
@Table(name = "legalcompliance_report_schedules")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReportSchedule extends AggregateRoot<ReportSchedule> {

    /** After this many failed runs in a row the creator is told, once. */
    public static final int FAILURES_BEFORE_ALERT = 3;

    @Column(nullable = false, length = 255) private String name;
    @Column(name = "report_key", nullable = false, length = 60) private String reportKey;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 10) private Frequency frequency;
    @Column(name = "run_day", nullable = false) private int runDay;
    @Column(nullable = false, length = 10) private String audience;
    @Column(name = "recipient_ids", nullable = false, columnDefinition = "TEXT") private String recipientIds;
    @Column(nullable = false) private boolean active = true;
    @Column(name = "next_run_on", nullable = false) private LocalDate nextRunOn;
    @Column(name = "last_run_on") private LocalDate lastRunOn;
    @Column(name = "last_status", nullable = false, length = 6) private String lastStatus = "NEVER";
    @Column(name = "last_row_count") private Integer lastRowCount;
    @Column(name = "last_error", length = 500) private String lastError;
    @Column(name = "consecutive_failures", nullable = false) private int consecutiveFailures;
    @Column(name = "created_by") private UUID createdBy;
    @Column(name = "created_by_name", length = 255) private String createdByName;

    public static ReportSchedule create(TenantId t, String name, String reportKey, Frequency f, int day, String audience, List<UUID> recipients, LocalDate today, UUID by, String byName) {
        ReportSchedule s = new ReportSchedule();
        s.initTenantId(t);
        s.reportKey = reportKey;
        s.audience = audience;
        s.createdBy = by;
        s.createdByName = byName;
        s.edit(name, f, day, recipients, today);
        return s;
    }

    /** The report and the audience cannot change: create a new schedule instead, so who may see what is never widened by an edit. */
    public void edit(String name, Frequency f, int day, List<UUID> recipients, LocalDate today) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("A name is required");
        ScheduleRules.validateDay(f, day);
        String ids = ScheduleRules.join(ScheduleRules.recipients(recipients));   // everything is validated before anything is changed
        LocalDate next = ScheduleRules.nextRun(f, day, today);
        this.name = name.trim();
        this.frequency = f;
        this.runDay = day;
        this.recipientIds = ids;
        this.nextRunOn = next;
    }

    public void setActive(boolean on, LocalDate today) {
        this.active = on;
        if (on) this.nextRunOn = ScheduleRules.nextRun(frequency, runDay, today);   // resuming never fires for the days missed while paused
    }

    public List<UUID> recipients() { return ScheduleRules.split(recipientIds); }

    public boolean isDue(LocalDate today) { return active && !nextRunOn.isAfter(today); }

    /** Taken before the run, so a second instance that read the same row loses the version check and skips it. Moves the date on whatever the outcome. */
    public void claim(LocalDate today) { this.nextRunOn = ScheduleRules.nextRun(frequency, runDay, today); }

    public void recordSuccess(LocalDate today, int rows) {
        lastRunOn = today; lastStatus = "OK"; lastRowCount = rows; lastError = null; consecutiveFailures = 0;
    }

    /** A failed run is tried again tomorrow rather than at the next period. */
    public void recordFailure(LocalDate today, String error) {
        lastRunOn = today; lastStatus = "FAILED"; lastRowCount = null;
        lastError = error == null ? "Unknown error" : error.length() > 500 ? error.substring(0, 500) : error;
        consecutiveFailures++;
        nextRunOn = today.plusDays(1);
    }

    public boolean justBecameAlertable() { return "FAILED".equals(lastStatus) && consecutiveFailures == FAILURES_BEFORE_ALERT; }
}
