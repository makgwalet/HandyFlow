package za.co.handyflow.platform.hr.domain.model;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.hr.domain.model.PayrollExceptionRules.Item;
import za.co.handyflow.platform.hr.domain.model.PayrollExceptionRules.Line;
import za.co.handyflow.platform.hr.domain.model.PayrollExceptionRules.Severity;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PayrollExceptionRulesTest {

    private final UUID a = UUID.randomUUID();
    private final UUID b = UUID.randomUUID();

    private static Line line(UUID id, String name, String net, boolean assumed, boolean noBank) {
        return new Line(id, name, "E" + name.length(), new BigDecimal(net), assumed, noBank);
    }

    private static boolean has(List<Item> items, String code) {
        return items.stream().anyMatch(i -> i.code().equals(code));
    }

    @Test
    void anEmptyRunIsAnError() {
        List<Item> items = PayrollExceptionRules.check(List.of(), null, Map.of());
        assertThat(has(items, "NO_PAYSLIPS")).isTrue();
        assertThat(PayrollExceptionRules.hasBlocking(items)).isTrue();
    }

    @Test
    void aCleanRunHasNothingToReport() {
        List<Item> items = PayrollExceptionRules.check(List.of(line(a, "Thandi", "20000.00", false, false)), null, Map.of());
        assertThat(items.isEmpty()).isTrue();
        assertThat(PayrollExceptionRules.hasBlocking(items)).isFalse();
    }

    @Test
    void zeroOrNegativeNetBlocks() {
        List<Item> items = PayrollExceptionRules.check(List.of(line(a, "Thandi", "0.00", false, false), line(b, "Sipho", "-5.00", false, false)), null, Map.of());
        assertThat(items.stream().filter(i -> i.code().equals("NET_NOT_POSITIVE")).count()).isEqualTo(2L);
        assertThat(PayrollExceptionRules.hasBlocking(items)).isTrue();
    }

    @Test
    void assumedMedicalAndMissingBankAreWarningsOnly() {
        List<Item> items = PayrollExceptionRules.check(List.of(line(a, "Thandi", "20000.00", true, true)), null, Map.of());
        assertThat(has(items, "MEDICAL_ASSUMED")).isTrue();
        assertThat(has(items, "BANK_DETAILS_MISSING")).isTrue();
        assertThat(items.stream().allMatch(i -> i.severity() == Severity.WARNING)).isTrue();
        assertThat(PayrollExceptionRules.hasBlocking(items)).isFalse();
    }

    @Test
    void aTenPercentMoveIsFineAndMoreIsFlagged() {
        Map<UUID, BigDecimal> prev = Map.of(a, new BigDecimal("20000.00"), b, new BigDecimal("20000.00"));
        List<Item> items = PayrollExceptionRules.check(List.of(
                line(a, "Thandi", "22000.00", false, false),
                line(b, "Sipho", "22000.01", false, false)), prev, Map.of());
        assertThat(items.stream().filter(i -> i.code().equals("NET_VARIANCE")).count()).isEqualTo(1L);
        assertThat(items.get(0).employee().startsWith("Sipho")).isTrue();
    }

    @Test
    void newAndMissingPeopleAreNamed() {
        Map<UUID, BigDecimal> prev = Map.of(b, new BigDecimal("15000.00"));
        List<Item> items = PayrollExceptionRules.check(List.of(line(a, "Thandi", "20000.00", false, false)), prev, Map.of(b, "Sipho Dlamini"));
        assertThat(has(items, "NEW_ON_RUN")).isTrue();
        Item missing = items.stream().filter(i -> i.code().equals("MISSING_FROM_RUN")).findFirst().orElseThrow();
        assertThat(missing.employee()).isEqualTo("Sipho Dlamini");
    }

    @Test
    void withoutAPreviousRunNothingIsCompared() {
        List<Item> items = PayrollExceptionRules.check(List.of(line(a, "Thandi", "20000.00", false, false)), null, Map.of());
        assertThat(has(items, "NEW_ON_RUN")).isFalse();
        assertThat(has(items, "NET_VARIANCE")).isFalse();
    }
}
