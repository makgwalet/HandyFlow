package za.co.handyflow.platform.security.application.internal;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.RowMapper;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class PayPremiumServiceTest {

    private static final UUID T = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ACTOR = UUID.fromString("33333333-3333-3333-3333-333333333331");
    private static final TenantId TENANT = TenantId.of(T);

    /** Records every statement and answers the two questions the service asks. */
    static class Rec extends JdbcTemplate {
        record Stmt(String kind, String sql, Object[] args) {}
        final List<Stmt> log = new ArrayList<>();
        Object[] settingsRow;                       // enabled, sunday, holiday
        final List<Object[]> overrideRows = new ArrayList<>(); // date, kind, name

        @Override public <X> List<X> query(String s, RowMapper<X> m, Object... a) {
            log.add(new Stmt("query", s, a));
            List<X> out = new ArrayList<>();
            try { if (settingsRow != null) out.add(m.mapRow(rs(settingsRow), 0)); } catch (java.sql.SQLException e) { throw new IllegalStateException(e); }
            return out;
        }
        @Override public void query(String s, RowCallbackHandler h, Object... a) {
            log.add(new Stmt("query", s, a));
            try { for (Object[] r : overrideRows) h.processRow(rs(r)); } catch (java.sql.SQLException e) { throw new IllegalStateException(e); }
        }
        @Override public int update(String s, Object... a) { log.add(new Stmt("update", s, a)); return 1; }
        List<Stmt> updates() { return log.stream().filter(x -> x.kind().equals("update")).toList(); }
    }

    /** A result set that answers by column number, the way the service reads it. */
    private static java.sql.ResultSet rs(Object[] row) {
        return (java.sql.ResultSet) Proxy.newProxyInstance(PayPremiumServiceTest.class.getClassLoader(), new Class<?>[]{java.sql.ResultSet.class}, (p, m, a) -> {
            int i = a != null && a.length > 0 && a[0] instanceof Integer n ? n - 1 : -1;
            return switch (m.getName()) {
                case "getBoolean" -> row[i];
                case "getBigDecimal" -> row[i];
                case "getString" -> row[i];
                case "getDate" -> java.sql.Date.valueOf((LocalDate) row[i]);
                default -> null;
            };
        });
    }

    private static PayPremiumService svc(Rec r) throws Exception {
        var k = PayPremiumService.class.getDeclaredConstructors()[0];
        PayPremiumService s = (PayPremiumService) (k.getParameterCount() == 0 ? k.newInstance() : k.newInstance(r));
        Field f = s.getClass().getDeclaredField("jdbc"); f.setAccessible(true); f.set(s, r);
        return s;
    }

    private static BigDecimal bd(String s) { return new BigDecimal(s); }
    private static LocalDate d(String s) { return LocalDate.parse(s); }
    private static int marks(String s) { int n = 0; for (char c : s.toCharArray()) if (c == '?') n++; return n; }

    // ---- settings ----

    @Test void aCompanyWithNoRowIsSwitchedOff() throws Exception {
        PayPremiumRules.Settings s = svc(new Rec()).settings(TENANT);
        assertFalse(s.enabled());
        assertEquals(PayPremiumRules.Settings.OFF, s);
    }

    @Test void savedSettingsAreRead() throws Exception {
        Rec r = new Rec(); r.settingsRow = new Object[]{true, bd("1.75"), bd("2.50")};
        PayPremiumService.SettingsView v = svc(r).settingsView(TENANT);
        assertTrue(v.enabled());
        assertEquals(bd("1.75"), v.sundayMultiplier());
        assertEquals(bd("2.50"), v.holidayMultiplier());
        assertEquals(T, r.log.get(0).args()[0]);
        assertTrue(r.log.get(0).sql().contains("tenant_id = ?"));
    }

    @Test void aBadMultiplierIsRefusedBeforeAnythingIsWritten() throws Exception {
        Rec r = new Rec();
        assertEquals("PREMIUM_MULTIPLIER", codeOf(() -> svc(r).save(TENANT, ACTOR, true, bd("1.2"), bd("2"))));
        assertEquals(0, r.updates().size());
    }

    @Test void savingWritesTheRowAndAnAuditEntry() throws Exception {
        Rec r = new Rec();
        PayPremiumService.SettingsView v = svc(r).save(TENANT, ACTOR, true, bd("1.5"), bd("2"));
        assertEquals(bd("1.50"), v.sundayMultiplier());
        assertEquals(2, r.updates().size());
        Rec.Stmt up = r.updates().get(0), au = r.updates().get(1);
        assertTrue(up.sql().contains("security_pay_premium_settings") && up.sql().contains("ON CONFLICT (tenant_id)"));
        assertArrayEquals(new Object[]{T, true, bd("1.50"), bd("2.00"), ACTOR}, up.args());
        assertEquals(marks(up.sql()), up.args().length);
        assertTrue(au.sql().contains("security_audit_log"));
        assertEquals("PAY_PREMIUM_SET", au.args()[3]);
        assertEquals(T, au.args()[0]); assertEquals(ACTOR, au.args()[1]);
        assertEquals(marks(au.sql()), au.args().length);
    }

    // ---- holidays ----

    @Test void theYearListMarksNationalAddedAndRemovedDays() throws Exception {
        Rec r = new Rec();
        r.overrideRows.add(new Object[]{d("2026-12-30"), "ADD", "Proclaimed"});
        r.overrideRows.add(new Object[]{d("2026-04-03"), "REMOVE", "Good Friday"});
        List<PayPremiumService.HolidayView> l = svc(r).holidays(TENANT, 2026);
        Map<LocalDate, String> src = new HashMap<>(); l.forEach(h -> src.put(h.date(), h.source()));
        assertEquals("ADDED", src.get(d("2026-12-30")));
        assertEquals("REMOVED", src.get(d("2026-04-03")));
        assertEquals("NATIONAL", src.get(d("2026-12-25")));
        assertEquals(SaPublicHolidays.of(2026).size() + 1, l.size());
        for (int i = 1; i < l.size(); i++) assertTrue(l.get(i - 1).date().isBefore(l.get(i).date()));
    }

    @Test void anAddedDayInAnotherYearIsNotListed() throws Exception {
        Rec r = new Rec(); r.overrideRows.add(new Object[]{d("2027-03-01"), "ADD", "Proclaimed"});
        assertEquals(SaPublicHolidays.of(2026).size(), svc(r).holidays(TENANT, 2026).size());
    }

    @Test void aSillyYearIsRefused() throws Exception {
        assertEquals("PREMIUM_YEAR", codeOf(() -> svc(new Rec()).holidays(TENANT, 1999)));
        assertEquals("PREMIUM_YEAR", codeOf(() -> svc(new Rec()).holidays(TENANT, 2101)));
    }

    // ---- adding and taking off days ----

    private static String codeOf(java.util.concurrent.Callable<?> c) {
        try { c.call(); throw new AssertionError("nothing thrown"); } catch (HandyFlowException e) { return e.getErrorCode(); } catch (Exception e) { throw new RuntimeException(e); }
    }

    @Test void aCompanyDayIsAddedWithATrimmedName() throws Exception {
        Rec r = new Rec();
        svc(r).add(TENANT, ACTOR, d("2026-12-30"), "  Proclaimed day  ");
        Rec.Stmt up = r.updates().get(0);
        assertTrue(up.sql().contains("security_public_holiday_overrides") && up.sql().contains("ON CONFLICT (tenant_id, holiday_date)"));
        assertArrayEquals(new Object[]{T, d("2026-12-30"), "ADD", "Proclaimed day", ACTOR}, up.args());
        assertEquals(marks(up.sql()), up.args().length);
        assertEquals("PUBLIC_HOLIDAY_ADD", r.updates().get(1).args()[3]);
    }

    @Test void anAddedDayNeedsAName() throws Exception {
        Rec r = new Rec();
        assertEquals("PREMIUM_NAME", codeOf(() -> svc(r).add(TENANT, ACTOR, d("2026-12-30"), "  ")));
        assertEquals("PREMIUM_NAME", codeOf(() -> svc(r).add(TENANT, ACTOR, d("2026-12-30"), null)));
        assertEquals("PREMIUM_NAME", codeOf(() -> svc(r).add(TENANT, ACTOR, d("2026-12-30"), "x".repeat(101))));
        assertEquals("PREMIUM_DATE", codeOf(() -> svc(r).add(TENANT, ACTOR, null, "Day")));
        assertEquals(0, r.updates().size());
    }

    @Test void aNationalDayCannotBeAddedTwice() throws Exception {
        Rec r = new Rec();
        assertEquals("PREMIUM_ALREADY", codeOf(() -> svc(r).add(TENANT, ACTOR, d("2026-12-25"), "Christmas")));
        assertEquals(0, r.updates().size());
    }

    @Test void aNationalDayTakenOffCanBePutBack() throws Exception {
        Rec r = new Rec(); r.overrideRows.add(new Object[]{d("2026-12-25"), "REMOVE", "Christmas Day"});
        svc(r).add(TENANT, ACTOR, d("2026-12-25"), null);
        assertTrue(r.updates().get(0).sql().startsWith("DELETE FROM security_public_holiday_overrides"));
        assertArrayEquals(new Object[]{T, d("2026-12-25")}, r.updates().get(0).args());
        assertEquals("PUBLIC_HOLIDAY_RESTORED", r.updates().get(1).args()[3]);
    }

    @Test void takingOffANationalDaySwitchesItOffAndKeepsTheRecord() throws Exception {
        Rec r = new Rec();
        svc(r).remove(TENANT, ACTOR, d("2026-12-25"));
        assertArrayEquals(new Object[]{T, d("2026-12-25"), "REMOVE", "Christmas Day", ACTOR}, r.updates().get(0).args());
        assertEquals("PUBLIC_HOLIDAY_REMOVE", r.updates().get(1).args()[3]);
    }

    @Test void takingOffADayTheCompanyAddedDeletesIt() throws Exception {
        Rec r = new Rec(); r.overrideRows.add(new Object[]{d("2026-12-30"), "ADD", "Proclaimed"});
        svc(r).remove(TENANT, ACTOR, d("2026-12-30"));
        assertTrue(r.updates().get(0).sql().startsWith("DELETE FROM security_public_holiday_overrides"));
        assertEquals("PUBLIC_HOLIDAY_CLEARED", r.updates().get(1).args()[3]);
    }

    @Test void takingOffADayThatIsNotAHolidayIsNotFound() throws Exception {
        Rec r = new Rec();
        assertEquals("PREMIUM_NOT_FOUND", codeOf(() -> svc(r).remove(TENANT, ACTOR, d("2026-11-23"))));
        assertEquals("PREMIUM_DATE", codeOf(() -> svc(r).remove(TENANT, ACTOR, null)));
        assertEquals(0, r.updates().size());
    }

    @Test void aDayAlreadyTakenOffCannotBeTakenOffAgain() throws Exception {
        Rec r = new Rec(); r.overrideRows.add(new Object[]{d("2026-12-25"), "REMOVE", "Christmas Day"});
        assertEquals("PREMIUM_ALREADY", codeOf(() -> svc(r).remove(TENANT, ACTOR, d("2026-12-25"))));
        assertEquals(0, r.updates().size());
    }

    // ---- what payroll gets ----

    @Test void theContextCarriesSettingsAndTheCompanysHolidays() throws Exception {
        Rec r = new Rec(); r.settingsRow = new Object[]{true, bd("1.50"), bd("2.00")};
        r.overrideRows.add(new Object[]{d("2026-12-30"), "ADD", "Proclaimed"});
        r.overrideRows.add(new Object[]{d("2026-04-03"), "REMOVE", "Good Friday"});
        PayPremiumService.Context c = svc(r).context(TENANT);
        assertTrue(c.settings().enabled());
        assertTrue(c.holidays().test(d("2026-12-30")));
        assertFalse(c.holidays().test(d("2026-04-03")));
        assertTrue(c.holidays().test(d("2026-12-25")));
        assertFalse(c.holidays().test(d("2026-11-24")));
    }
}
