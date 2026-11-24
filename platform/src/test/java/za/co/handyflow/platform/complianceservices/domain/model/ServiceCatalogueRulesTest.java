package za.co.handyflow.platform.complianceservices.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ServiceCatalogueRulesTest {

    @Test @DisplayName("an item has a known kind and real text, and is cleaned up")
    void item() {
        var i = ServiceCatalogueRules.item(" deliverable ", "  Annual return filed  ", true);
        assertThat(i.kind()).isEqualTo("DELIVERABLE");
        assertThat(i.text()).isEqualTo("Annual return filed");
        assertThatThrownBy(() -> ServiceCatalogueRules.item("TASK", "Something", true)).hasMessageContaining("DELIVERABLE");
        assertThatThrownBy(() -> ServiceCatalogueRules.item("CHECKLIST", "ab", true)).hasMessageContaining("3 characters");
        assertThatThrownBy(() -> ServiceCatalogueRules.item("CHECKLIST", "x".repeat(301), true)).hasMessageContaining("300");
        assertThatThrownBy(() -> ServiceCatalogueRules.item(null, "Something", true)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test @DisplayName("the same wording twice in one kind is refused, in different kinds it is fine")
    void duplicates() {
        var a = ServiceCatalogueRules.item("CHECKLIST", "Collect ID", true);
        var b = ServiceCatalogueRules.item("CHECKLIST", "collect id", false);
        var c = ServiceCatalogueRules.item("DOCUMENT", "Collect ID", true);
        ServiceCatalogueRules.noDuplicates(List.of(a, c));
        assertThatThrownBy(() -> ServiceCatalogueRules.noDuplicates(List.of(a, b))).hasMessageContaining("twice");
    }

    @Test @DisplayName("hours are rounded to 2 places and must be above 0 and at most 2000")
    void hours() {
        assertThat(ServiceCatalogueRules.hours(null)).isNull();
        assertThat(ServiceCatalogueRules.hours(new BigDecimal("1.505"))).isEqualByComparingTo("1.51");
        assertThat(ServiceCatalogueRules.hours(new BigDecimal("2000"))).isEqualByComparingTo("2000");
        assertThatThrownBy(() -> ServiceCatalogueRules.hours(BigDecimal.ZERO)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ServiceCatalogueRules.hours(new BigDecimal("-1"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ServiceCatalogueRules.hours(new BigDecimal("2000.01"))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test @DisplayName("turnaround is 1 to 365 days or not set")
    void sla() {
        assertThat(ServiceCatalogueRules.slaDays(null)).isNull();
        assertThat(ServiceCatalogueRules.slaDays(30)).isEqualTo(30);
        assertThatThrownBy(() -> ServiceCatalogueRules.slaDays(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ServiceCatalogueRules.slaDays(366)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test @DisplayName("review needs the essentials and at least one deliverable; a later version also says what changed")
    void missing() {
        assertThat(ServiceCatalogueRules.missingForReview("CIPC annual return", "CIPC", "FIXED", "ON_DELIVERY", "CONSULTANT", BigDecimal.ONE, 1, 1, null)).isEmpty();
        assertThat(ServiceCatalogueRules.missingForReview(null, null, null, null, null, null, 0, 1, null)).hasSize(7);
        assertThat(ServiceCatalogueRules.missingForReview("n", "CIPC", "FIXED", "ON_DELIVERY", "CONSULTANT", BigDecimal.ONE, 1, 2, null)).containsExactly("what changed");
    }
}
