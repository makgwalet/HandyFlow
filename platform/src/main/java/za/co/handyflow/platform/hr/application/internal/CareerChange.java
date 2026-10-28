package za.co.handyflow.platform.hr.application.internal;

import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.UUID;

/** Published inside the transaction that changes an employee, so the career line is saved with the change or not at all. */
public record CareerChange(TenantId tenantId, UUID employeeId, String kind, LocalDate date, String jobTitle, String department, String from, String to,
                           String note, boolean payRelated, UUID actor) {

    public static CareerChange of(TenantId tenantId, UUID employeeId, String kind, LocalDate date, String from, String to, String note, UUID actor) {
        return new CareerChange(tenantId, employeeId, kind, date, null, null, from, to, note, false, actor);
    }
}
