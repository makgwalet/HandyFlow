package za.co.handyflow.platform.hr;

import za.co.handyflow.platform.shared.DomainEvent;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

/**
 * FIX: backlog 3.3 — "contracting ↔ HR wiring unconfirmed." Confirmed
 * genuinely disconnected: neither module's package-info.java allows a
 * dependency on the other, and HrService.createEmployee() had zero
 * reference to contracting. This event is the wiring — same pattern as
 * identity.TenantCreatedEvent (a plain DomainEvent published at the root
 * of the publishing module's package, consumed elsewhere via
 * {@code @ApplicationModuleListener}, see
 * billing.application.internal.BillingEventHandlers for that precedent).
 * <p>
 * Carries only what HrEmployee actually captures at creation time — see
 * contracting.application.internal.ContractingHrEventHandler's own
 * Javadoc for why several of the BCEA employment-template's variables
 * (job duties, working hours, probation length, etc.) are
 * deliberately left for HR to fill in afterward via contracting's
 * existing "leave blank to fill in later" edit flow, not guessed at here.
 */
public record EmployeeCreatedEvent(
        TenantId tenantId,
        UUID employeeId,
        String firstName,
        String lastName,
        String idNumber,
        String jobTitle,
        LocalDate startDate,
        BigDecimal grossSalary,
        Map<String, String> address,   // the employee's home address as HR holds it (street, suburb, city, postalCode); may be null
        LocalDate endDate,             // set for a fixed-term engagement; null for a permanent one
        Instant occurredOn
) implements DomainEvent {

    public static EmployeeCreatedEvent of(TenantId tenantId, UUID employeeId,
                                          String firstName, String lastName,
                                          String idNumber, String jobTitle,
                                          LocalDate startDate, BigDecimal grossSalary) {
        return of(tenantId, employeeId, firstName, lastName, idNumber, jobTitle, startDate, grossSalary, null, null);
    }

    /** As above, with the address and end date HR holds at creation. The address is copied, with blank values dropped. */
    public static EmployeeCreatedEvent of(TenantId tenantId, UUID employeeId,
                                          String firstName, String lastName,
                                          String idNumber, String jobTitle,
                                          LocalDate startDate, BigDecimal grossSalary,
                                          Map<String, String> address, LocalDate endDate) {
        Map<String, String> copy = new java.util.HashMap<>();
        if (address != null) address.forEach((k, v) -> { if (k != null && v != null && !v.isBlank()) copy.put(k, v); });
        return new EmployeeCreatedEvent(tenantId, employeeId, firstName, lastName,
                idNumber, jobTitle, startDate, grossSalary, Map.copyOf(copy), endDate, Instant.now());
    }
}