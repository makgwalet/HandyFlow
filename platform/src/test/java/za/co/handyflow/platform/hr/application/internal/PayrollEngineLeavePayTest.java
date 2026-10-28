package za.co.handyflow.platform.hr.application.internal;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.hr.domain.model.HrEmployee;
import za.co.handyflow.platform.shared.StatutoryYearRules;
import za.co.handyflow.platform.shared.StatutoryYearRules.Bracket;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Annual leave paid out on leaving is an annual payment: the extra tax the year bears, not the month annualised with it. */
class PayrollEngineLeavePayTest {

    private static BigDecimal d(String s) { return new BigDecimal(s); }

    private static final StatutoryYearRules Y2026 = new StatutoryYearRules(2026,
            LocalDate.of(2025, 3, 1), LocalDate.of(2026, 2, 28),
            List.of(new Bracket(d("0"), d("237100"), d("0"), d("18")),
                    new Bracket(d("237100"), d("370500"), d("42678"), d("26")),
                    new Bracket(d("370500"), d("512800"), d("77362"), d("31")),
                    new Bracket(d("512800"), d("673000"), d("121475"), d("36")),
                    new Bracket(d("673000"), d("857900"), d("179147"), d("39")),
                    new Bracket(d("857900"), d("1817000"), d("251258"), d("41")),
                    new Bracket(d("1817000"), null, d("644489"), d("45"))),
            d("17235"), d("9444"), d("3145"), d("364"), d("364"), d("246"),
            d("17712"), d("0.01"), d("0.01"), d("0.01"), d("500000"), d("350000"), d("0.275"), d("0.8"));

    private final PayrollEngine engine = new PayrollEngine();
    private final HrEmployee jane = HrEmployee.create(TenantId.of(UUID.randomUUID()), "EMP-1", "Jane", "Dlamini",
            LocalDate.of(2020, 1, 1), "PERMANENT", d("30000"), "MONTHLY");

    private PayrollEngine.PayrollResult calc(String basic, String payout, String bonus) {
        return engine.calculate(jane, Y2026, d("5000000"), 0, BigDecimal.ZERO, new PayrollEngine.Period(d(basic), BigDecimal.ZERO, BigDecimal.ZERO, d(payout),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, d(bonus), BigDecimal.ZERO, BigDecimal.ZERO));
    }

    @Test
    void leavePayIsTaxedExactlyLikeABonusOfTheSameAmount() {
        assertThat(calc("15000", "40000", "0").payeAmount()).isEqualByComparingTo(calc("15000", "0", "40000").payeAmount());
    }

    @Test
    void itIsNotAnnualisedWithTheMonth() {
        // Annualised with the month, 15 000 + 40 000 = 55 000 x 12 = 660 000 a year, tax 117 800 less rebate 17 235 = 100 565 a year, 8 380.42 a month.
        // As an annual payment the month stays 15 000 x 12 = 180 000 (tax 32 400 - 17 235 = 15 165, 1 263.75 a month) and the 40 000 bears the extra
        // tax of the year at 180 000 + 40 000 = 220 000: 39 600 - 17 235 = 22 365, less 15 165 = 7 200. Together 8 463.75.
        assertThat(calc("15000", "40000", "0").payeAmount()).isEqualByComparingTo("8463.75");
    }

    @Test
    void theEarningsAndTheUifAreTheSameAsBefore() {
        PayrollEngine.PayrollResult r = calc("15000", "40000", "0");
        assertThat(r.grossSalary()).isEqualByComparingTo("15000");
        assertThat(r.leavePayout()).isEqualByComparingTo("40000");
        assertThat(r.totalEarnings()).isEqualByComparingTo("55000");
        assertThat(r.uifEmployee()).isEqualByComparingTo("177.12");   // the ceiling applies to the month's pay and the payout together
    }

    @Test
    void noPayoutChangesNothing() {
        assertThat(calc("30000", "0", "0").payeAmount()).isEqualByComparingTo("4783.08");
    }
}
