package za.co.handyflow.platform.payroll;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import za.co.handyflow.platform.hr.application.internal.PayrollEngine;
import za.co.handyflow.platform.hr.domain.model.HrEmployee;
import za.co.handyflow.platform.payrollbureau.application.internal.PayrollBureauEngine;
import za.co.handyflow.platform.payrollbureau.domain.model.PayEmployee;
import za.co.handyflow.platform.shared.SarsTaxRebate;
import za.co.handyflow.platform.shared.SarsTaxRebateRepository;
import za.co.handyflow.platform.shared.SarsTaxTable;
import za.co.handyflow.platform.shared.SarsTaxTableRepository;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Formalizes what PayrollBureauEngine's own class Javadoc already
 * documents as an accepted, deliberate trade-off — see this class's
 * prior revision for the full rationale. This revision fixes every
 * signature flagged as unverified last round against real, confirmed
 * source:
 * <ul>
 *   <li>SarsTaxTableRepository/SarsTaxRebateRepository both use
 *       findByTaxYear(int) — confirmed directly, not
 *       findByTaxYearOrderByIncomeFromAsc (that method never existed).</li>
 *   <li>hr.PayrollEngine.PayrollResult's PAYE accessor is genuinely
 *       .payeAmount() — same name as payrollbureau's own, the earlier
 *       .monthlyPaye() guess was simply wrong.</li>
 *   <li>HrEmployee.create()/PayEmployee.create() confirmed directly —
 *       neither factory takes travelAllowance/pension/medicalAid; both
 *       default those to ZERO and require the matching setter calls
 *       afterward (setTravelAllowance/setPensionContribution/
 *       setMedicalAidContribution — confirmed present on both entities).</li>
 * </ul>
 * <p>
 * SarsTaxTable/SarsTaxRebate are partially mocked (CALLS_REAL_METHODS)
 * rather than fully mocked — both classes only have a no-arg constructor
 * and getters, no public factory. Stubbing only the getters lets
 * SarsTaxTable's REAL contains()/calculateTax() methods run against
 * controlled values, rather than mocking away the actual formula this
 * test exists to protect.
 */
class PayrollEngineParityTest {

    @Mock private SarsTaxTableRepository taxTableRepo;
    @Mock private SarsTaxRebateRepository taxRebateRepo;

    private PayrollEngine hrEngine;
    private PayrollBureauEngine bureauEngine;

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID PAY_CLIENT_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        hrEngine = new PayrollEngine(taxTableRepo, taxRebateRepo);
        bureauEngine = new PayrollBureauEngine(taxTableRepo, taxRebateRepo);

        // FIX: SarsTaxTable.contains()/calculateTax() read their fields
        // DIRECTLY (annualIncome.compareTo(incomeFrom)), not through the
        // getter. A partial mock with stubbed getters leaves the actual
        // field null — confirmed by a real NullPointerException at
        // exactly this line when this test first ran. Building genuinely
        // real instances via reflection instead, since neither class
        // exposes a builder/setter, only a no-arg constructor + getters.
        SarsTaxTable bracket = newInstance(SarsTaxTable.class);
        setField(bracket, "taxYear", 2026);
        setField(bracket, "incomeFrom", BigDecimal.ZERO);
        setField(bracket, "incomeTo", null);
        setField(bracket, "baseTax", BigDecimal.ZERO);
        setField(bracket, "marginalRate", new BigDecimal("18.00"));
        when(taxTableRepo.findByTaxYear(2026)).thenReturn(List.of(bracket));

        SarsTaxRebate primary = newInstance(SarsTaxRebate.class);
        setField(primary, "taxYear", 2026);
        setField(primary, "rebateType", "PRIMARY");
        setField(primary, "amount", new BigDecimal("17235.00"));
        when(taxRebateRepo.findByTaxYear(2026)).thenReturn(List.of(primary));
    }

    /** Both SarsTaxTable/SarsTaxRebate have only a no-arg constructor and getters — no builder. */
    private static <T> T newInstance(Class<T> type) {
        try {
            var ctor = type.getDeclaredConstructor();
            ctor.setAccessible(true);
            return ctor.newInstance();
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to construct " + type.getSimpleName() + " for test fixture", e);
        }
    }

    private static void setField(Object target, String fieldName, Object value) {
        try {
            var field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to set field '" + fieldName + "' on "
                    + target.getClass().getSimpleName() + " test fixture — confirm the real field name/type", e);
        }
    }

    @ParameterizedTest(name = "monthlySalary={0} travelAllowance={1} pension={2} medicalAid={3}")
    @CsvSource({
            "25000.00, 0,       0,       0",
            "45000.00, 5000.00, 3000.00, 1200.00",
            "120000.00, 15000.00, 20000.00, 0",
    })
    void hrAndBureauEnginesProduceIdenticalStatutoryDeductions(
            BigDecimal monthlySalary, BigDecimal travelAllowance,
            BigDecimal pension, BigDecimal medicalAid) {

        int taxYear = 2026;
        BigDecimal annualPayroll = monthlySalary.multiply(BigDecimal.valueOf(12));
        LocalDate startDate = LocalDate.of(2024, 1, 1);

        HrEmployee hrEmployee = HrEmployee.create(TenantId.of(TENANT_ID), "EMP-PARITY",
                "Parity", "Test", startDate, "PERMANENT", monthlySalary, "MONTHLY");
        hrEmployee.setTravelAllowance(travelAllowance);
        hrEmployee.setPensionContribution(pension);
        hrEmployee.setMedicalAidContribution(medicalAid);

        PayEmployee bureauEmployee = PayEmployee.create(TENANT_ID, PAY_CLIENT_ID, "EMP-PARITY",
                "Parity", "Test", startDate, monthlySalary);
        bureauEmployee.setTravelAllowance(travelAllowance);
        bureauEmployee.setPensionContribution(pension);
        bureauEmployee.setMedicalAidContribution(medicalAid);

        PayrollEngine.PayrollResult hrResult = hrEngine.calculate(hrEmployee, taxYear, annualPayroll);
        PayrollBureauEngine.PayrollResult bureauResult = bureauEngine.calculate(bureauEmployee, taxYear, annualPayroll);

        // The actual compliance-critical figures — if these ever diverge,
        // one of the two engines is now calculating incorrect statutory
        // deductions for real employees.
        assertThat(bureauResult.payeAmount())
                .as("PAYE must match between hr and payrollbureau engines")
                .isEqualByComparingTo(hrResult.payeAmount());
        assertThat(bureauResult.uifEmployee())
                .as("UIF (employee) must match")
                .isEqualByComparingTo(hrResult.uifEmployee());
        assertThat(bureauResult.uifEmployer())
                .as("UIF (employer) must match")
                .isEqualByComparingTo(hrResult.uifEmployer());
        assertThat(bureauResult.sdlAmount())
                .as("SDL must match")
                .isEqualByComparingTo(hrResult.sdlAmount());
        assertThat(bureauResult.taxableIncome())
                .as("Taxable income must match")
                .isEqualByComparingTo(hrResult.taxableIncome());
    }
}