package za.co.handyflow.platform.payrollbureau.application.internal;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.payrollbureau.domain.model.PayEmployee;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.StatutoryRulesProvider;
import za.co.handyflow.platform.shared.StatutoryYearRules;
import za.co.handyflow.platform.shared.StatutoryYearRules.Bracket;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** The bureau engine takes its figures from the approved rules and refuses a year without them (slice 1a). */
class PayrollBureauEngineTest {

    private static BigDecimal d(String s) { return new BigDecimal(s); }

    private static final StatutoryYearRules Y2027 = new StatutoryYearRules(2027,
            LocalDate.of(2026, 3, 1), LocalDate.of(2027, 2, 28),
            List.of(new Bracket(d("0"), d("245100"), d("0"), d("18")),
                    new Bracket(d("245100"), d("383100"), d("44118"), d("26")),
                    new Bracket(d("383100"), null, d("79998"), d("31"))),
            d("17820"), d("9765"), d("3249"), d("376"), d("376"), d("254"),
            d("17712"), d("0.01"), d("0.01"), d("0.01"), d("500000"), d("430000"), d("0.275"), d("0.8"));

    private final StatutoryRulesProvider provider = new StatutoryRulesProvider() {
        @Override public StatutoryYearRules rulesFor(int taxYear) {
            return find(taxYear).orElseThrow(() -> new IllegalStateException("There are no approved statutory rules for tax year " + taxYear));
        }
        @Override public Optional<StatutoryYearRules> find(int taxYear) {
            return taxYear == 2027 ? Optional.of(Y2027) : Optional.empty();
        }
    };
    private final PayrollBureauEngine engine = new PayrollBureauEngine(provider);

    private PayEmployee employee(String gross, String medical) {
        PayEmployee e = mock(PayEmployee.class);
        when(e.getGrossSalary()).thenReturn(d(gross));
        when(e.getTravelAllowance()).thenReturn(BigDecimal.ZERO);
        when(e.getPensionContribution()).thenReturn(BigDecimal.ZERO);
        when(e.getMedicalAidContribution()).thenReturn(d(medical));
        when(e.getDateOfBirth()).thenReturn(LocalDate.of(1990, 6, 1));
        return e;
    }

    @Test
    void payeBelowTheSecondBracketIsTheFirstBracketLessTheRebate() {
        // 240 000 a year: 18% = 43 200, less the 17 820 rebate = 25 380, a twelfth is 2 115.00
        var r = engine.calculate(employee("20000", "0"), engine.requireRules(2027), d("240000"));
        assertEquals(0, d("2115.00").compareTo(r.payeAmount()));
        assertEquals(0, d("177.12").compareTo(r.uifEmployee()));   // capped at the R17 712 ceiling
        assertEquals(0, BigDecimal.ZERO.compareTo(r.sdlAmount())); // payroll under the R500 000 threshold
    }

    @Test
    void aMedicalSchemeMemberGetsTheTaxpayersMonthlyCredit() {
        // 360 000 a year: 44 118 + 26% of 114 900 = 73 992, less rebate 17 820, less 12 x 376 credit = 51 660, a twelfth is 4 305.00
        var r = engine.calculate(employee("30000", "1500"), engine.requireRules(2027), d("360000"));
        assertEquals(0, d("4305.00").compareTo(r.payeAmount()));
    }

    @Test
    void aPayslipRecordsTheRulesAndInputsItWasCalculatedFrom() {
        PayEmployee e = employee("20000", "0");
        var rules = engine.requireRules(2027);
        var r = engine.calculate(e, rules, d("240000"));
        var slip = za.co.handyflow.platform.payrollbureau.domain.model.Payslip.create(java.util.UUID.randomUUID(),
                java.util.UUID.randomUUID(), java.util.UUID.randomUUID(), r.monthlySalary(), r.travelAllowance(), r.payeAmount(),
                r.uifEmployee(), r.uifEmployer(), r.sdlAmount(), r.medicalAid(), r.pension(), r.taxableIncome(), r.taxYear());

        engine.freeze(slip, e, rules, d("240000"));

        assertEquals(LocalDate.of(2026, 3, 1), slip.getRulesEffectiveFrom());
        assertEquals(64, slip.getRulesFingerprint().length());
        assertEquals("shared-paye-1", slip.getFormulaVersion());
        assertEquals(0, slip.getInputMedicalMembers());
        assertEquals(0, d("240000").compareTo(slip.getInputAnnualPayroll()));
        assertThrows(IllegalStateException.class, () -> engine.freeze(slip, e, rules, d("240000")));
    }

    @Test
    void aYearWithoutApprovedRulesIsRefusedNotCalculatedAsZero() {
        HandyFlowException e = assertThrows(HandyFlowException.class, () -> engine.requireRules(2030));
        assertTrue(e.getMessage().contains("no approved statutory rules"));
    }
}
