package za.co.handyflow.platform.supplychain.application.internal;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.supplychain.application.internal.SupplierReturnRules.Line;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SupplierReturnRulesTest {

    private static BigDecimal bd(String s) { return new BigDecimal(s); }

    @Test
    void aReturnMovesThroughDispatchBeforeCredit_aClaimSkipsIt() {
        assertThat(SupplierReturnRules.canMove("RETURN", "DRAFT", "SUBMITTED")).isTrue();
        assertThat(SupplierReturnRules.canMove("RETURN", "SUBMITTED", "APPROVED")).isTrue();
        assertThat(SupplierReturnRules.canMove("RETURN", "APPROVED", "DISPATCHED")).isTrue();
        assertThat(SupplierReturnRules.canMove("RETURN", "DISPATCHED", "CREDITED")).isTrue();
        assertThat(SupplierReturnRules.canMove("RETURN", "APPROVED", "CREDITED")).as("credit before the goods left").isFalse();
        assertThat(SupplierReturnRules.canMove("CLAIM", "APPROVED", "CREDITED")).isTrue();
        assertThat(SupplierReturnRules.canMove("CLAIM", "APPROVED", "DISPATCHED")).as("a claim moves no stock").isFalse();
    }

    @Test
    void approvalCannotBeSkipped() {
        assertThat(SupplierReturnRules.canMove("RETURN", "DRAFT", "APPROVED")).isFalse();
        assertThat(SupplierReturnRules.canMove("RETURN", "DRAFT", "DISPATCHED")).isFalse();
        assertThat(SupplierReturnRules.canMove("CLAIM", "SUBMITTED", "CREDITED")).isFalse();
    }

    @Test
    void finalStatesStayFinal_andNothingIsCancelledOnceGoodsHaveLeft() {
        for (String s : List.of("CREDITED", "REJECTED", "CANCELLED"))
            for (String to : List.of("DRAFT", "SUBMITTED", "APPROVED", "DISPATCHED", "CREDITED", "REJECTED", "CANCELLED"))
                assertThat(SupplierReturnRules.canMove("RETURN", s, to)).as(s + "->" + to).isFalse();
        assertThat(SupplierReturnRules.canMove("RETURN", "DISPATCHED", "CANCELLED")).isFalse();
        assertThat(SupplierReturnRules.canMove("RETURN", "SUBMITTED", "REJECTED")).isTrue();
    }

    @Test
    void lineValidation() {
        assertThat(SupplierReturnRules.lineProblem(List.of())).contains("at least one");
        assertThat(SupplierReturnRules.lineProblem(List.of(new Line(bd("0"), bd("10"))))).contains("above zero");
        assertThat(SupplierReturnRules.lineProblem(List.of(new Line(bd("1"), bd("-1"))))).contains("unit cost");
        assertThat(SupplierReturnRules.lineProblem(List.of(new Line(bd("2"), bd("0"))))).isNull();
    }

    @Test
    void expectedCreditIsQtyTimesCost_toTheCent() {
        assertThat(SupplierReturnRules.expectedCredit(List.of(new Line(bd("3"), bd("10.005")), new Line(bd("0.5"), bd("7")))))
                .isEqualByComparingTo("33.52");
    }

    @Test
    void aReceiptLineCannotComeBackTwice() {
        Map<String, BigDecimal> accepted = Map.of("L1", bd("10"));
        assertThat(SupplierReturnRules.overReturn(Map.of("L1", bd("4")), accepted, Map.of())).isNull();
        assertThat(SupplierReturnRules.overReturn(Map.of("L1", bd("10")), accepted, Map.of())).isNull();
        assertThat(SupplierReturnRules.overReturn(Map.of("L1", bd("6")), accepted, Map.of("L1", bd("5")))).contains("Only 5");
        assertThat(SupplierReturnRules.overReturn(Map.of("L1", bd("1")), accepted, Map.of("L1", bd("10")))).contains("Only 0");
        assertThat(SupplierReturnRules.overReturn(Map.of("GHOST", bd("1")), accepted, Map.of())).contains("Only 0");
    }

    @Test
    void aCreditNoteAboveTheClaimIsRefused_butFiveCentsOfRoundingIsNot() {
        assertThat(SupplierReturnRules.creditExceedsExpected(bd("100.00"), bd("100.05"))).isFalse();
        assertThat(SupplierReturnRules.creditExceedsExpected(bd("100.00"), bd("100.06"))).isTrue();
        assertThat(SupplierReturnRules.creditExceedsExpected(bd("100.00"), bd("60.00"))).as("a smaller credit is the supplier's call").isFalse();
    }
}
