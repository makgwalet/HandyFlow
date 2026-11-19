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
                "notice_period_weeks", "job_duties", "employer_reg"}) {
            assertFalse(v.containsKey(key), key + " must be left for staff");
        }
    }

    @Test
    void worksWithoutATenantProfile() {
        Map<String, String> v = ContractingHrEventHandler.buildVariables(event, null);
        assertEquals(5, v.size());
    }

    private EmployeeCreatedEvent withAddress(Map<String, String> address, LocalDate end) {
        return EmployeeCreatedEvent.of(TenantId.of(UUID.randomUUID()), UUID.randomUUID(), "Thandi", "Nkosi", "8001015009087",
                "Site Foreman", LocalDate.of(2026, 12, 1), new BigDecimal("32000.00"), address, end);
    }

    @Test
    void fillsTheEmployeesOwnAddressFromHr() {
        Map<String, String> v = ContractingHrEventHandler.buildVariables(
                withAddress(Map.of("street", "7 Protea Ave", "suburb", "Glenwood", "city", "Durban", "postalCode", "4001"), null), null);
        assertEquals("7 Protea Ave, Glenwood, Durban, 4001", v.get("employee_address"));
    }

    @Test
    void anEmployeeAddressIsNeverMistakenForTheEmployers() {
        Map<String, String> v = ContractingHrEventHandler.buildVariables(
                withAddress(Map.of("street", "7 Protea Ave", "city", "Durban"), null), tenant("Acme", Map.of("street", "1 Main Rd", "city", "Cape Town")));
        assertEquals("7 Protea Ave, Durban", v.get("employee_address"));
        assertEquals("1 Main Rd, Cape Town", v.get("employer_address"));
    }

    @Test
    void aBlankOrMissingAddressLeavesTheVariableForStaff() {
        assertFalse(ContractingHrEventHandler.buildVariables(withAddress(Map.of("street", "  "), null), null).containsKey("employee_address"));
        assertFalse(ContractingHrEventHandler.buildVariables(withAddress(null, null), null).containsKey("employee_address"));
    }

    @Test
    void theEventCopiesTheAddressSoLaterChangesDoNotReachIt() {
        Map<String, String> live = new java.util.HashMap<>(Map.of("street", "7 Protea Ave"));
        EmployeeCreatedEvent e = withAddress(live, LocalDate.of(2027, 11, 30));
        live.put("street", "changed");
        assertEquals("7 Protea Ave", e.address().get("street"));
        assertEquals(LocalDate.of(2027, 11, 30), e.endDate());
        assertThrows(UnsupportedOperationException.class, () -> e.address().put("x", "y"));
    }

    @Test
    void theShortFormStillWorksForCallersThatHoldNoAddress() {
        assertTrue(event.address().isEmpty());
        assertNull(event.endDate());
    }
}
