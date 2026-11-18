package za.co.handyflow.platform.contracting.application.internal;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.hr.EmployeeCreatedEvent;
import za.co.handyflow.platform.identity.TenantDetails;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ContractingHrVariablesTest {

    private final EmployeeCreatedEvent event = EmployeeCreatedEvent.of(TenantId.of(UUID.randomUUID()), UUID.randomUUID(),
            "Thandi", "Nkosi", "8001015009087", "Site Foreman", LocalDate.of(2026, 12, 1), new BigDecimal("32000.00"));

    private TenantDetails tenant(String name, Map<String, String> address) {
        return new TenantDetails(UUID.randomUUID(), name, "slug", null, null, null, address, null,
                null, null, null, null, null, null, null);
    }

    @Test
    void fillsHrAndTenantVariables() {
        Map<String, String> v = ContractingHrEventHandler.buildVariables(event,
                tenant("Acme Civils (Pty) Ltd", Map.of("street", "1 Main Rd", "city", "Durban", "postalCode", "4001")));
        assertEquals("Thandi Nkosi", v.get("employee_name"));
        assertEquals("8001015009087", v.get("employee_id_number"));
        assertEquals("Site Foreman", v.get("job_title"));
        assertEquals("2026-12-01", v.get("start_date"));
        assertEquals("32000.00", v.get("basic_salary"));
        assertEquals("Acme Civils (Pty) Ltd", v.get("employer_name"));
        assertEquals("1 Main Rd, Durban, 4001", v.get("employer_address"));
        assertEquals("Durban", v.get("city_name"));
    }

    @Test
    void leavesLegalTermsForStaffInsteadOfAssumingDefaults() {
        Map<String, String> v = ContractingHrEventHandler.buildVariables(event, tenant("Acme", Map.of("city", "Durban")));
        for (String key : new String[]{"probation_months", "working_hours", "pay_day", "annual_leave_days",
                "notice_period_weeks", "job_duties", "employee_address", "employer_reg"}) {
            assertFalse(v.containsKey(key), key + " must be left for staff");
        }
    }

    @Test
    void worksWithoutATenantProfile() {
        Map<String, String> v = ContractingHrEventHandler.buildVariables(event, null);
        assertEquals(5, v.size());
    }
}
