package za.co.handyflow.platform.hr;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.hr.dto.EmployeeResponse;

import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/** What other modules are given about an employee, and what they are not. */
class EmployeeSummaryTest {

    private static final Set<String> NEVER_SHARED = Set.of("idNumber", "taxNumber", "dateOfBirth", "gender", "race", "grossSalary", "payFrequency",
            "medicalAidContribution", "pensionContribution", "travelAllowance", "emergencyContactName", "emergencyContactPhone");

    @Test
    void theSummaryHasNoFieldForIdentityNumbersTaxOrPay() {
        Set<String> names = Arrays.stream(EmployeeSummary.class.getRecordComponents()).map(RecordComponent::getName).collect(Collectors.toSet());
        assertThat(names.stream().filter(NEVER_SHARED::contains).toList()).isEmpty();
        assertThat(Arrays.stream(EmployeePay.class.getRecordComponents()).map(RecordComponent::getName).toList()).containsExactly("grossSalary", "payFrequency");
    }

    @Test
    void itCarriesWhoTheyAreAndHowToReachThem() {
        UUID id = UUID.randomUUID();
        EmployeeResponse full = new EmployeeResponse(id, "EMP-0007", "Gerhard", "Botha", "Gerhard Botha", "8001015009087", "1234567890",
                LocalDate.of(1980, 1, 1), "M", "African", "g@example.com", "0821234567", "PERMANENT", "Security officer", "Operations",
                LocalDate.of(2020, 3, 1), null, "ACTIVE", new BigDecimal("32000.00"), "MONTHLY", BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                "Mia", "0820000000", Instant.now());
        EmployeeSummary s = EmployeeSummary.from(full);
        assertThat(s.id()).isEqualTo(id);
        assertThat(s.employeeNumber()).isEqualTo("EMP-0007");
        assertThat(s.fullName()).isEqualTo("Gerhard Botha");
        assertThat(s.email()).isEqualTo("g@example.com");
        assertThat(s.phone()).isEqualTo("0821234567");
        assertThat(s.jobTitle()).isEqualTo("Security officer");
        assertThat(s.department()).isEqualTo("Operations");
        assertThat(s.status()).isEqualTo("ACTIVE");
        assertThat(s.toString()).doesNotContain("8001015009087").doesNotContain("1234567890").doesNotContain("32000");
    }
}
