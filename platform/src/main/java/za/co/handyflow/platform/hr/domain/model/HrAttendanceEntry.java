package za.co.handyflow.platform.hr.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** One time in and (once they leave) one time out. Never deleted: a wrong entry is corrected or voided and the change is logged. */
@Entity
@Table(name = "hr_attendance_entries")
@Getter
@NoArgsConstructor
public class HrAttendanceEntry {

    public static final String ACTIVE = "ACTIVE", VOID = "VOID", CLOCK = "CLOCK", MANUAL = "MANUAL";

    @Id UUID id;
    @Column(name = "tenant_id")   UUID tenantId;
    @Column(name = "employee_id") UUID employeeId;
    @Column(name = "work_date")   LocalDate workDate;
    @Column(name = "clock_in")    Instant clockIn;
    @Column(name = "clock_out")   Instant clockOut;
    String source;
    String status = ACTIVE;
    @Column(name = "created_by")  UUID createdBy;
    @Column(name = "created_at")  Instant createdAt;
    @Column(name = "updated_at")  Instant updatedAt;
    @Version long version;

    public static HrAttendanceEntry of(UUID tenantId, UUID employeeId, Instant in, Instant out, String source, UUID by) {
        HrAttendanceEntry e = new HrAttendanceEntry();
        e.id = UUID.randomUUID();
        e.tenantId = tenantId;
        e.employeeId = employeeId;
        e.clockIn = in;
        e.clockOut = out;
        e.workDate = AttendanceRules.workDate(in);
        e.source = source;
        e.createdBy = by;
        e.createdAt = Instant.now();
        e.updatedAt = e.createdAt;
        return e;
    }

    public void clockOutAt(Instant out) { this.clockOut = out; this.updatedAt = Instant.now(); }

    public void correct(Instant in, Instant out) {
        this.clockIn = in;
        this.clockOut = out;
        this.workDate = AttendanceRules.workDate(in);
        this.updatedAt = Instant.now();
    }

    public void voidEntry() { this.status = VOID; this.updatedAt = Instant.now(); }

    public boolean isOpen() { return clockOut == null && ACTIVE.equals(status); }
}
