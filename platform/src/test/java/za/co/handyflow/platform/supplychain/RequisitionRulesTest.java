package za.co.handyflow.platform.supplychain;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.supplychain.application.internal.RequisitionRules;
import za.co.handyflow.platform.supplychain.application.internal.RequisitionRules.Availability;
import za.co.handyflow.platform.supplychain.application.internal.RequisitionRules.Fulfilment;
import za.co.handyflow.platform.supplychain.application.internal.RequisitionRules.LineInput;
import za.co.handyflow.platform.supplychain.domain.enums.RequisitionStatus;
import za.co.handyflow.platform.supplychain.domain.enums.RequisitionType;
import za.co.handyflow.platform.supplychain.domain.model.ScRequisition;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RequisitionRulesTest {

    private static BigDecimal bd(String v) { return new BigDecimal(v); }
    private static LineInput line(String qty, String cost) { return new LineInput("Widget", bd(qty), bd(cost), bd("15")); }

    @Test
    void validation_catchesMissingFields() {
        assertThat(RequisitionRules.validate(RequisitionType.OPERATIONAL, "Paper", "Office use", null, List.of(line("5", "10")))).isEmpty();
        assertThat(RequisitionRules.validate(null, " ", "", null, List.of(new LineInput("", bd("0"), null, bd("15")))))
                .hasSizeGreaterThanOrEqualTo(6);
        assertThat(RequisitionRules.validate(RequisitionType.PROJECT, "Cable", "Site 4", " ", List.of(line("1", "1"))))
                .anyMatch(e -> e.contains("project reference"));
    }

    @Test
    void estimatedTotal_includesVat() {
        // 10 x 100 = 1000 + 150 VAT, plus 2 x 50.50 = 101.00 + 15.15 VAT -> 1266.15
        assertThat(RequisitionRules.estimatedTotal(List.of(line("10", "100"), line("2", "50.50")))).isEqualByComparingTo("1266.15");
    }

    @Test
    void advisories_flagCoverageAndDuplicateDemand() {
        var covered = RequisitionRules.advisories("Widget", bd("20"), new Availability(bd("8"), bd("15"), bd("0")));
        assertThat(covered).singleElement().satisfies(m -> assertThat(m).contains("already on order cover"));

        var partial = RequisitionRules.advisories("Widget", bd("50"), new Availability(bd("8"), bd("15"), bd("10")));
        assertThat(partial).hasSize(2);
        assertThat(partial.get(1)).contains("other open requisitions");

        assertThat(RequisitionRules.advisories("Widget", bd("5"), Availability.NONE)).isEmpty();
    }

    @Test
    void fulfilment_isDerivedFromOrderedQuantities() {
        assertThat(RequisitionRules.fulfilment(List.<BigDecimal[]>of(new BigDecimal[]{bd("10"), bd("0")}))).isEqualTo(Fulfilment.NOT_ORDERED);
        assertThat(RequisitionRules.fulfilment(List.<BigDecimal[]>of(new BigDecimal[]{bd("10"), bd("4")}))).isEqualTo(Fulfilment.PARTIALLY_ORDERED);
        assertThat(RequisitionRules.fulfilment(List.of(new BigDecimal[]{bd("10"), bd("10")}, new BigDecimal[]{bd("5"), bd("5")})))
                .isEqualTo(Fulfilment.ORDERED);
        assertThat(RequisitionRules.fulfilment(List.of(new BigDecimal[]{bd("10"), bd("10")}, new BigDecimal[]{bd("5"), bd("1")})))
                .isEqualTo(Fulfilment.PARTIALLY_ORDERED);
    }

    @Test
    void suggestedQty_usesReorderQtyElseTopsUpToTwiceReorderPoint() {
        assertThat(RequisitionRules.suggestedQty(bd("50"), bd("10"), bd("3"))).isEqualByComparingTo("50");
        assertThat(RequisitionRules.suggestedQty(bd("0"), bd("10"), bd("3"))).isEqualByComparingTo("17");   // 20 - 3
        assertThat(RequisitionRules.suggestedQty(null, bd("10"), bd("25"))).isEqualByComparingTo("0");
    }

    @Test
    void parseType_isForgiving() {
        assertThat(RequisitionRules.parseType(" capex ")).isEqualTo(RequisitionType.CAPEX);
        assertThat(RequisitionRules.parseType("nonsense")).isNull();
        assertThat(RequisitionRules.parseType(null)).isNull();
    }

    @Test
    void statusMachine() {
        UUID user = UUID.randomUUID();
        ScRequisition r = ScRequisition.create(UUID.randomUUID(), "PR-00001", RequisitionType.OPERATIONAL, "Paper", "Office",
                null, null, null, null, null, "ZAR", user, "Tester");
        assertThat(r.getStatus()).isEqualTo(RequisitionStatus.DRAFT);
        assertThatThrownBy(() -> r.approve(user, "Boss", null)).isInstanceOf(IllegalStateException.class);   // can't approve a draft

        r.submit();
        assertThatThrownBy(() -> r.edit(RequisitionType.OPERATIONAL, "x", "y", null, null, null, null, null, null))
                .isInstanceOf(IllegalStateException.class);                                                  // locked while pending
        r.withdraw();
        assertThat(r.isEditable()).isTrue();

        r.submit();
        r.reject("Not budgeted");
        assertThat(r.getStatus()).isEqualTo(RequisitionStatus.REJECTED);
        assertThatThrownBy(() -> r.cancel("x")).isInstanceOf(IllegalStateException.class);
        r.reopen();
        r.submit();
        r.approve(UUID.randomUUID(), "Boss", "ok");
        assertThat(r.getStatus()).isEqualTo(RequisitionStatus.APPROVED);
        r.cancel("No longer needed");
        assertThat(r.getStatus()).isEqualTo(RequisitionStatus.CANCELLED);
    }
}
