package za.co.handyflow.platform.hr.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;

/** A pattern of ordinary hours: which days, from when to when, with how much unpaid break. */
@Entity
@Table(name = "hr_work_schedules")
@Getter
@NoArgsConstructor
public class HrWorkSchedule {

    @Id UUID id;
    @Column(name = "tenant_id")     UUID tenantId;
    String name;
    String days;
    @Column(name = "start_time")    LocalTime startTime;
    @Column(name = "end_time")      LocalTime endTime;
    @Column(name = "break_minutes") int breakMinutes;
    @Column(name = "is_default")    boolean defaultSchedule;
    boolean active = true;
    @Column(name = "created_at")    Instant createdAt;
    @Column(name = "updated_at")    Instant updatedAt;
    @Version long version;

    public static HrWorkSchedule of(UUID tenantId, String name, AttendanceRules.Schedule s, boolean defaultSchedule) {
        HrWorkSchedule w = new HrWorkSchedule();
        w.id = UUID.randomUUID();
        w.tenantId = tenantId;
        w.defaultSchedule = defaultSchedule;
        w.createdAt = Instant.now();
        w.apply(name, s);
        return w;
    }

    public void apply(String name, AttendanceRules.Schedule s) {
        this.name = name.trim();
        this.days = AttendanceRules.daysCsv(s.days());
        this.startTime = s.start();
        this.endTime = s.end();
        this.breakMinutes = s.breakMinutes();
        this.updatedAt = Instant.now();
    }

    public void setDefaultSchedule(boolean d) { this.defaultSchedule = d; this.updatedAt = Instant.now(); }
    public void setActive(boolean a) { this.active = a; if (!a) this.defaultSchedule = false; this.updatedAt = Instant.now(); }

    public AttendanceRules.Schedule toSchedule() {
        return new AttendanceRules.Schedule(AttendanceRules.parseDays(days), startTime, endTime, breakMinutes);
    }
}
