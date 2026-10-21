package za.co.handyflow.platform.catalogue.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class HistoryRulesTest {
    private static String redact(CatalogueAuditAction a, String d, boolean see) { return HistoryRules.redact(a, d, see); }

    @Test
    void someoneWhoMaySeePricesSeesEverything() {
        assertThat(redact(CatalogueAuditAction.UPDATED, "name: A -> B; cost: 1.00 -> 2.00", true)).isEqualTo("name: A -> B; cost: 1.00 -> 2.00");
        assertThat(redact(CatalogueAuditAction.PRICE_PROPOSED, "Trade: 1 -> 2", true)).isEqualTo("Trade: 1 -> 2");
    }

    @Test
    void costIsRemovedFromAnEditLineButTheRestStays() {
        assertThat(redact(CatalogueAuditAction.UPDATED, "name: A -> B; cost: 1.00 -> 2.00; unit: each -> kg", false)).isEqualTo("name: A -> B; unit: each -> kg");
        assertThat(redact(CatalogueAuditAction.UPDATED, "cost: 1.00 -> 2.00", false)).isNull();
    }

    @Test
    void theMinimumPriceIsRemoved() {
        assertThat(redact(CatalogueAuditAction.UPDATED, "minimum price: — -> R 80.00", false)).isNull();
    }

    @Test
    void aPriceChangeKeepsTheSellingPriceButNotCostOrVat() {
        assertThat(redact(CatalogueAuditAction.PRICE_CHANGED, "price: 100.00 -> 120.00; cost: 60.00 -> 70.00; VAT %: 15.00 -> 0.00", false)).isEqualTo("price: 100.00 -> 120.00");
    }

    @Test
    void aPriceChangeFromThePricingWorkflowIsHidden() {
        assertThat(redact(CatalogueAuditAction.PRICE_CHANGED, "Trade: (none) -> 90.00, from 2026-10-21", false)).isNull();
    }

    @Test
    void theApprovalWorkflowLinesAreHidden() {
        for (var a : new CatalogueAuditAction[]{CatalogueAuditAction.PRICE_PROPOSED, CatalogueAuditAction.PRICE_APPROVED, CatalogueAuditAction.PRICE_REJECTED, CatalogueAuditAction.PRICE_CANCELLED})
            assertThat(redact(a, "Retail: 100.00 -> 120.00 (+20.0%), from 2026-10-21. reason", false)).isNull();
    }

    @Test
    void otherLinesAndNullsPassThrough() {
        assertThat(redact(CatalogueAuditAction.STATUS_CHANGED, "Active -> Suspended: stock", false)).isEqualTo("Active -> Suspended: stock");
        assertThat(redact(CatalogueAuditAction.CREATED, null, false)).isNull();
    }
}
