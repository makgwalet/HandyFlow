package za.co.handyflow.platform.supplychain.application.internal;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.supplychain.application.internal.QualityInspectionRules.Result;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class QualityInspectionRulesTest {
    private static BigDecimal bd(String s) { return new BigDecimal(s); }

    @Test
    void aLineCanOnlyInspectWhatTheReceiptAccepted() {
        assertThat(QualityInspectionRules.lineProblem(bd("10"), bd("8"), bd("2"))).isNull();
        assertThat(QualityInspectionRules.lineProblem(bd("10"), bd("3"), bd("0"))).isNull();       // a spot check is allowed
        assertThat(QualityInspectionRules.lineProblem(bd("10"), bd("8"), bd("3"))).contains("more than the 10");
        assertThat(QualityInspectionRules.lineProblem(bd("10"), bd("-1"), bd("0"))).contains("negative");
        assertThat(QualityInspectionRules.lineProblem(bd("10"), null, bd("0"))).contains("required");
    }

    @Test
    void resultIsPassedPartialOrFailed_andNothingUntilSomethingIsInspected() {
        assertThat(QualityInspectionRules.result(List.of())).isNull();
        assertThat(QualityInspectionRules.result(List.of(new Result(bd("0"), bd("0"), bd("0"))))).isNull();
        assertThat(QualityInspectionRules.result(List.of(new Result(bd("10"), bd("10"), bd("0"))))).isEqualTo("PASSED");
        assertThat(QualityInspectionRules.result(List.of(new Result(bd("10"), bd("7"), bd("3"))))).isEqualTo("PARTIAL");
        assertThat(QualityInspectionRules.result(List.of(new Result(bd("10"), bd("0"), bd("10"))))).isEqualTo("FAILED");
        assertThat(QualityInspectionRules.result(List.of(new Result(bd("5"), bd("5"), bd("0")), new Result(bd("5"), bd("0"), bd("5")))))
                .as("one good line does not hide a bad one").isEqualTo("PARTIAL");
    }

    @Test
    void passRate() {
        assertThat(QualityInspectionRules.passRatePct(0, 0)).isNull();
        assertThat(QualityInspectionRules.passRatePct(9, 10)).isEqualTo(90.0);
        assertThat(QualityInspectionRules.passRatePct(12, 10)).isEqualTo(100.0);
    }
}
