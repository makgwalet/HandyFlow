package za.co.handyflow.platform.hr.domain.model;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** An overpayment recovered from net pay changes what is paid out, and nothing that tax is worked out on. */
class HrPayslipRecoveryTest {

    private static BigDecimal d(String s) { return new BigDecimal(s); }

    private HrPayslip slip() {
        HrPayslip p = HrPayslip.create(TenantId.of(UUID.randomUUID()), UUID.randomUUID(), UUID.randomUUID(), d("30000"), BigDecimal.ZERO, d("1000"), d("1500"));
        p.applyCalculations(d("3000.00"), d("300.00"), d("300.00"), d("300.00"), d("330000"), d("90000"), d("17235"), 2027);
        return p;
    }

    @Test
    void aRecoveryLowersNetPayAndTotalDeductionsButNotTaxOrEarnings() {
        HrPayslip p = slip();
        BigDecimal net = p.getNetPay(), earnings = p.getTotalEarnings(), deductions = p.getTotalDeductions();

        p.recover(d("1250.50"));

        assertEquals(0, net.subtract(d("1250.50")).compareTo(p.getNetPay()));
        assertEquals(0, deductions.add(d("1250.50")).compareTo(p.getTotalDeductions()));
        assertEquals(0, earnings.compareTo(p.getTotalEarnings()));
        assertEquals(0, d("3000.00").compareTo(p.getPayeAmount()));
        assertEquals(0, d("1250.50").compareTo(p.getOtherDeductions()));
    }

    @Test
    void nothingToRecoverChangesNothing() {
        HrPayslip p = slip();
        BigDecimal net = p.getNetPay();
        p.recover(null);
        p.recover(BigDecimal.ZERO);
        assertEquals(0, net.compareTo(p.getNetPay()));
    }
}
