package za.co.handyflow.platform.hr.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** One movement of leave days. Append-only: the database refuses updates and deletes. */
@Entity
@Table(name = "hr_leave_ledger")
@Getter
@NoArgsConstructor
public class HrLeaveLedger {

    @Id UUID id;
    @Column(name = "tenant_id")   UUID tenantId;
    @Column(name = "employee_id") UUID employeeId;
    @Column(name = "leave_type")  String leaveType;
    @Column(name = "entry_date")  LocalDate entryDate;
    String kind;
    BigDecimal days;
    @Column(name = "request_id")  UUID requestId;
    @Column(name = "period_key")  String periodKey;
    String note;
    @Column(name = "created_by")  UUID createdBy;
    @Column(name = "created_at")  Instant createdAt;

    public static HrLeaveLedger of(UUID tenantId, UUID employeeId, String leaveType, LocalDate entryDate, String kind,
                                   BigDecimal days, UUID requestId, String periodKey, String note, UUID actor) {
        HrLeaveLedger l = new HrLeaveLedger();
        l.id = UUID.randomUUID();
        l.tenantId = tenantId;
        l.employeeId = employeeId;
        l.leaveType = leaveType;
        l.entryDate = entryDate;
        l.kind = kind;
        l.days = days;
        l.requestId = requestId;
        l.periodKey = periodKey;
        l.note = note == null || note.isBlank() ? null : (note.length() > 300 ? note.substring(0, 300) : note.trim());
        l.createdBy = actor;
        l.createdAt = Instant.now();
        return l;
    }
}
