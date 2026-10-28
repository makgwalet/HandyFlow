package za.co.handyflow.platform.hr.domain.model;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Severance adds to earnings and its directed tax to deductions; the monthly PAYE stays as it was worked out. */
class HrPayslipSeveranceTest {

    private static BigDecimal d(String s) { return new BigDecimal(s); }

    private HrPayslip slip(boolean withSeverance) {
        HrPayslip p = HrPayslip.create(TenantId.of(UUID.randomUUID()), UUID.randomUUID(), UUID.randomUUID(), d("30000"), BigDecimal.ZERO, d("1000"), d("1500"));
        if (withSeverance) p.setSeverance(d("60000.00"), d("4500.50"), "D-1");
        p.applyCalculations(d("3000.00"), d("300.00"), d("300.00"), d("300.00"), d("330000"), d("90000"), d("17235"), 2027);
        return p;
    }

    @Test
    void severanceIsPaidLessTheDirectedTaxAndNothingElseChanges() {
        HrPayslip plain = slip(false), with = slip(true);
        assertEquals(0, plain.getTotalEarnings().add(d("60000.00")).compareTo(with.getTotalEarnings()));
        assertEquals(0, plain.getTotalDeductions().add(d("4500.50")).compareTo(with.getTotalDeductions()));
        assertEquals(0, plain.getNetPay().add(d("55499.50")).compareTo(with.getNetPay()));
        assertEquals(0, plain.getPayeAmount().compareTo(with.getPayeAmount()));
        assertEquals(0, plain.getUifEmployee().compareTo(with.getUifEmployee()));
        assertEquals("D-1", with.getSeveranceDirective());
        assertEquals(0, d("4500.50").compareTo(with.getSeveranceTax()));
    }

    @Test
    void aSlipWithoutSeveranceHasNone() {
        HrPayslip p = slip(false);
        assertEquals(0, BigDecimal.ZERO.compareTo(p.getSeveranceAmount()));
        assertEquals(0, BigDecimal.ZERO.compareTo(p.getSeveranceTax()));
        assertNull(p.getSeveranceDirective());
        p.setSeverance(null, null, null);
        assertEquals(0, BigDecimal.ZERO.compareTo(p.getSeveranceAmount()));
    }

    @Test
    void aRecoveryAfterSeveranceStillComesOffNetPay() {
        HrPayslip p = slip(true);
        BigDecimal net = p.getNetPay();
        p.recover(d("1000"));
        assertEquals(0, net.subtract(d("1000")).compareTo(p.getNetPay()));
    }
}
