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

/** One line of an employee's career: written by the system when something changes, or by HR for earlier employers and promotions. */
@Entity
@Table(name = "hr_career_events")
@Getter
@NoArgsConstructor
public class HrCareerEvent {

    @Id UUID id;
    @Column(name = "tenant_id")   UUID tenantId;
    @Column(name = "employee_id") UUID employeeId;
    String kind;
    @Column(name = "event_date")  LocalDate eventDate;
    @Column(name = "end_date")    LocalDate endDate;
    String employer;
    @Column(name = "job_title")   String jobTitle;
    String department;
    @Column(name = "from_value")  String fromValue;
    @Column(name = "to_value")    String toValue;
    String note;
    @Column(name = "pay_related") boolean payRelated;
    boolean auto;
    @Column(name = "created_by")  UUID createdBy;
    @Column(name = "created_at")  Instant createdAt;
    @Column(name = "removed_at")  Instant removedAt;
    @Column(name = "removed_by")  UUID removedBy;

    public static HrCareerEvent of(UUID tenantId, UUID employeeId, String kind, LocalDate date, LocalDate endDate, String employer, String jobTitle,
                                   String department, String from, String to, String note, boolean payRelated, boolean auto, UUID by) {
        HrCareerEvent e = new HrCareerEvent();
        e.id = UUID.randomUUID();
        e.tenantId = tenantId;
        e.employeeId = employeeId;
        e.kind = kind;
        e.eventDate = date;
        e.endDate = endDate;
        e.employer = employer;
        e.jobTitle = jobTitle;
        e.department = department;
        e.fromValue = from;
        e.toValue = to;
        e.note = note;
        e.payRelated = payRelated;
        e.auto = auto;
        e.createdBy = by;
        e.createdAt = Instant.now();
        return e;
    }

    public void remove(UUID by) {
        this.removedAt = Instant.now();
        this.removedBy = by;
    }
}
