package za.co.handyflow.platform.hr.domain.model;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** The incentive comes off the PAYE of the return, never off UIF or SDL, and never takes PAYE below nil. */
class Emp201EtiTest {

    private static BigDecimal d(String s) { return new BigDecimal(s); }
    private final TenantId tenant = TenantId.of(UUID.randomUUID());

    private HrEmp201 emp(String paye, String eti) {
        return HrEmp201.create(tenant, UUID.randomUUID(), LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), d(paye), d("500"), d("100"), d(eti));
    }

    @Test
    void theIncentiveReducesWhatIsPayable() {
        HrEmp201 e = emp("10000", "3000");
        assertThat(e.getTotalEti()).isEqualByComparingTo("3000.00");
        assertThat(e.getEtiCarried()).isEqualByComparingTo("0.00");
        assertThat(e.getTotalPayable()).isEqualByComparingTo("7600.00");   // 10 000 - 3 000 + 500 + 100
        assertThat(e.getTotalPaye()).isEqualByComparingTo("10000");
    }

    @Test
    void whatExceedsThePayeIsCarriedForwardAndUifAndSdlAreStillPaid() {
        HrEmp201 e = emp("1200", "3000");
        assertThat(e.getTotalEti()).isEqualByComparingTo("1200.00");
        assertThat(e.getEtiCarried()).isEqualByComparingTo("1800.00");
        assertThat(e.getTotalPayable()).isEqualByComparingTo("600.00");   // 0 PAYE + 500 + 100
    }

    @Test
    void withoutTheIncentiveNothingChanges() {
        HrEmp201 e = HrEmp201.create(tenant, UUID.randomUUID(), LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), d("10000"), d("500"), d("100"));
        assertThat(e.getTotalEti()).isEqualByComparingTo("0");
        assertThat(e.getTotalPayable()).isEqualByComparingTo("10600");
    }
}
