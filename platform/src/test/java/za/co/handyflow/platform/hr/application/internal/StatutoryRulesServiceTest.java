package za.co.handyflow.platform.hr.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.hr.domain.model.HrStatutoryTaxYear;
import za.co.handyflow.platform.hr.domain.repository.HrStatutoryTaxYearRepository;
import za.co.handyflow.platform.hr.dto.TaxYearResponse;
import za.co.handyflow.platform.shared.*;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StatutoryRulesServiceTest {

    private HrStatutoryTaxYearRepository years;
    private SarsTaxTableRepository tables;
    private SarsTaxRebateRepository rebates;
    private StatutoryRulesService service;

    @BeforeEach
    void setUp() {
        years = mock(HrStatutoryTaxYearRepository.class);
        tables = mock(SarsTaxTableRepository.class);
        rebates = mock(SarsTaxRebateRepository.class);
        service = new StatutoryRulesService(years, tables, rebates);
    }

    private static <T> T make(Class<T> type, Object... fieldsAndValues) {
        try {
            var c = type.getDeclaredConstructor(); c.setAccessible(true);
            T o = c.newInstance();
            for (int i = 0; i < fieldsAndValues.length; i += 2) {
                Field f = type.getDeclaredField((String) fieldsAndValues[i]); f.setAccessible(true); f.set(o, fieldsAndValues[i + 1]);
            }
            return o;
        } catch (ReflectiveOperationException e) { throw new RuntimeException(e); }
    }

    private static BigDecimal d(String s) { return new BigDecimal(s); }

    private HrStatutoryTaxYear year(int y, String status) {
        return make(HrStatutoryTaxYear.class, "taxYear", y, "label", "2026/27", "status", status,
                "effectiveFrom", LocalDate.of(y - 1, 3, 1), "effectiveTo", LocalDate.of(y, 2, 28),
                "uifMonthlyCeiling", d("17712"), "uifRateEmployee", d("0.01"), "uifRateEmployer", d("0.01"),
                "sdlRate", d("0.01"), "sdlThreshold", d("500000"), "retirementCapAmount", d("430000"),
                "retirementCapPct", d("0.275"), "travelTaxablePct", d("0.8"),
                "mtcTaxpayer", d("376"), "mtcFirstDependant", d("376"), "mtcAdditional", d("254"),
                "sourceNote", "SARS", "approvedAt", "APPROVED".equals(status) ? Instant.now() : null);
    }

    private SarsTaxTable line(int y, String from, String to, String base, String rate) {
        return make(SarsTaxTable.class, "taxYear", y, "incomeFrom", d(from), "incomeTo", to == null ? null : d(to), "baseTax", d(base), "marginalRate", d(rate));
    }

    private SarsTaxRebate rebate(int y, String type, String amount) {
        return make(SarsTaxRebate.class, "taxYear", y, "rebateType", type, "amount", d(amount));
    }

    private void loadComplete(int y) {
        when(years.findById(y)).thenReturn(Optional.of(year(y, "APPROVED")));
        when(tables.findByTaxYear(y)).thenReturn(List.of(line(y, "0", "245100", "0", "18"), line(y, "245100", null, "44118", "26")));
        when(rebates.findByTaxYear(y)).thenReturn(List.of(rebate(y, "PRIMARY", "17820"), rebate(y, "SECONDARY", "9765"), rebate(y, "TERTIARY", "3249")));
    }

    @Test
    void anApprovedCompleteYearGivesItsRules() {
        loadComplete(2027);
        StatutoryYearRules r = service.rulesFor(2027);
        assertThat(r.taxYear()).isEqualTo(2027);
        assertThat(r.brackets().size()).isEqualTo(2);
        assertThat(r.primaryRebate()).isEqualByComparingTo("17820");
        assertThat(r.monthlyMedicalCredit(3)).isEqualByComparingTo("1006");
        assertThat(r.retirementCapAmount()).isEqualByComparingTo("430000");
    }

    @Test
    void aYearThatDoesNotExistIsRefusedWithAPlainMessage() {
        when(years.findById(2030)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.rulesFor(2030)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no approved statutory rules for tax year 2030")
                .hasMessageContaining("1 March 2029");
    }

    @Test
    void aDraftYearIsNotUsed() {
        when(years.findById(2027)).thenReturn(Optional.of(year(2027, "DRAFT")));
        assertThat(service.find(2027)).isEmpty();
    }

    @Test
    void anApprovedYearWithoutBracketsOrRebatesIsNotUsed() {
        when(years.findById(2027)).thenReturn(Optional.of(year(2027, "APPROVED")));
        when(tables.findByTaxYear(2027)).thenReturn(List.of());
        assertThat(service.find(2027)).isEmpty();

        when(tables.findByTaxYear(2027)).thenReturn(List.of(line(2027, "0", null, "0", "18")));
        when(rebates.findByTaxYear(2027)).thenReturn(List.of(rebate(2027, "PRIMARY", "17820")));
        assertThat(service.find(2027)).isEmpty();
    }

    @Test
    void theListSaysWhyAYearCannotBeUsed() {
        when(years.allNewestFirst()).thenReturn(List.of(year(2028, "DRAFT")));
        when(tables.findByTaxYear(2028)).thenReturn(List.of());
        when(rebates.findByTaxYear(2028)).thenReturn(List.of());
        TaxYearResponse r = service.list().get(0);
        assertThat(r.usable()).isFalse();
        assertThat(r.problems().size()).isEqualTo(3);
    }

    @Test
    void theListMarksACompleteApprovedYearUsable() {
        loadComplete(2027);
        when(years.allNewestFirst()).thenReturn(List.of(year(2027, "APPROVED")));
        TaxYearResponse r = service.list().get(0);
        assertThat(r.usable()).isTrue();
        assertThat(r.bracketCount()).isEqualTo(2);
    }
}
