package za.co.handyflow.platform.hr.application.internal;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.hr.domain.model.HrEmp201;
import za.co.handyflow.platform.hr.domain.model.HrPayRun;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** One EMP201 per tax month: runs join an open return, a filed return is left alone, and totals follow the runs. */
class Emp201MergeTest {

    private final TenantId tenant = TenantId.of(UUID.randomUUID());
    private final Emp201Fakes f = new Emp201Fakes();
    private final LocalDate start = LocalDate.of(2026, 10, 1), end = LocalDate.of(2026, 10, 31);

    private HrPayRun run(String no, String paye, String uif, String sdl) {
        HrPayRun r = Emp201Fakes.run(tenant, no, start, end, paye, uif, sdl);
        f.add(r);
        return r;
    }

    private static void setStatus(HrPayRun r, String status) {
        try {
            java.lang.reflect.Field f = HrPayRun.class.getDeclaredField("status");
            f.setAccessible(true);
            f.set(r, status);
        } catch (ReflectiveOperationException ex) { throw new IllegalStateException(ex); }
    }

    private static BigDecimal d(String v) { return new BigDecimal(v); }

    @Test
    void twoRunsInTheSameMonthShareOneReturn() {
        HrPayRun a = run("PR1", "1000.00", "100.00", "50.00"), b = run("PR2", "400.00", "40.00", "20.00");
        HrEmp201 first = f.merge.attach(tenant, a);
        HrEmp201 second = f.merge.attach(tenant, b);
        assertTrue(first == second);
        assertEquals(1, f.returns.size());
        assertEquals(d("1400.00"), first.getTotalPaye());
        assertEquals(d("140.00"), first.getTotalUif());
        assertEquals(d("70.00"), first.getTotalSdl());
        assertEquals(d("1610.00"), first.getTotalPayable());
        assertEquals(2, f.runIdsOf(first).size());
    }

    @Test
    void recalculatingARunDoesNotCountItTwice() {
        HrPayRun a = run("PR1", "1000.00", "100.00", "50.00");
        f.merge.attach(tenant, a);
        f.merge.attach(tenant, a);
        assertEquals(1, f.returns.size());
        assertEquals(1, f.links.size());
        assertEquals(d("1150.00"), f.returns.get(0).getTotalPayable());
    }

    @Test
    void aFiledReturnIsNeverChangedAndALaterRunGetsItsOwn() {
        HrPayRun a = run("PR1", "1000.00", "100.00", "50.00");
        HrEmp201 filed = f.merge.attach(tenant, a);
        filed.markSubmitted("712345678901", UUID.randomUUID());
        HrPayRun b = run("PR2", "300.00", "30.00", "10.00");
        HrEmp201 extra = f.merge.attach(tenant, b);
        assertTrue(filed != extra);
        assertEquals(2, f.returns.size());
        assertEquals(d("1150.00"), filed.getTotalPayable());
        assertEquals(d("340.00"), extra.getTotalPayable());
    }

    @Test
    void aRunInAFiledReturnCannotBeTakenOut() {
        HrPayRun a = run("PR1", "1000.00", "100.00", "50.00");
        HrEmp201 filed = f.merge.attach(tenant, a);
        filed.markSubmitted("712345678901", UUID.randomUUID());
        assertThrows(IllegalStateException.class, () -> f.merge.detach(tenant, a.getId()));
        assertEquals(1, f.links.size());
    }

    @Test
    void takingOutOneRunWorksTheTotalsAgain() {
        HrPayRun a = run("PR1", "1000.00", "100.00", "50.00"), b = run("PR2", "400.00", "40.00", "20.00");
        HrEmp201 e = f.merge.attach(tenant, a);
        f.merge.attach(tenant, b);
        f.merge.detach(tenant, b.getId());
        assertEquals(d("1000.00"), e.getTotalPaye());
        assertEquals(d("1150.00"), e.getTotalPayable());
    }

    @Test
    void aReturnWithNoRunsLeftIsRemoved() {
        HrPayRun a = run("PR1", "1000.00", "100.00", "50.00");
        f.merge.attach(tenant, a);
        f.merge.detach(tenant, a.getId());
        assertTrue(f.returns.isEmpty());
        assertTrue(f.links.isEmpty());
    }

    @Test
    void aCancelledRunDropsOutOfTheTotals() {
        HrPayRun a = run("PR1", "1000.00", "100.00", "50.00"), b = run("PR2", "400.00", "40.00", "20.00");
        HrEmp201 e = f.merge.attach(tenant, a);
        f.merge.attach(tenant, b);
        setStatus(b, "CANCELLED");
        f.merge.recompute(e);
        assertEquals(d("1150.00"), e.getTotalPayable());
    }

    @Test
    void theIncentiveIsTakenOffThePayeAndTheExcessIsCarried() {
        HrPayRun a = run("PR1", "1000.00", "100.00", "50.00"), b = run("PR2", "400.00", "40.00", "20.00");
        f.eti.put(a.getId(), d("300.00"));
        f.eti.put(b.getId(), d("200.00"));
        HrEmp201 e = f.merge.attach(tenant, a);
        f.merge.attach(tenant, b);
        assertEquals(d("500.00"), e.getTotalEti());
        assertEquals(d("1110.00"), e.getTotalPayable());
        f.eti.put(a.getId(), d("2000.00"));
        f.merge.recompute(e);
        assertEquals(d("1400.00"), e.getTotalEti());
        assertEquals(d("800.00"), e.getEtiCarried());
        assertEquals(d("210.00"), e.getTotalPayable());
    }

    @Test
    void allIssuedLooksAtEveryRunOfTheReturn() {
        HrPayRun a = run("PR1", "1000.00", "100.00", "50.00"), b = run("PR2", "400.00", "40.00", "20.00");
        HrEmp201 e = f.merge.attach(tenant, a);
        f.merge.attach(tenant, b);
        a.approve(UUID.randomUUID());
        a.finalise(UUID.randomUUID());
        assertFalse(f.merge.allIssued(tenant, e));
        assertFalse(f.merge.issuedReturns(java.util.List.of(e)).contains(e.getId()));
        b.approve(UUID.randomUUID());
        b.finalise(UUID.randomUUID());
        assertTrue(f.merge.allIssued(tenant, e));
        assertTrue(f.merge.issuedReturns(java.util.List.of(e)).contains(e.getId()));
    }
}
