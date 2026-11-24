package za.co.handyflow.platform.complianceservices.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClientPartyRulesTest {

    private static BigDecimal bd(String s) { return new BigDecimal(s); }
    private static ClientPartyRules.Holding h(String role, String pct, boolean active) {
        return new ClientPartyRules.Holding(role, pct == null ? null : bd(pct), active);
    }

    @Test @DisplayName("a correct South African ID number passes, a wrong check digit or date fails")
    void saId() {
        assertThat(ClientPartyRules.validSaId("8001015009087")).isTrue();
        assertThat(ClientPartyRules.validSaId("8001015009088")).isFalse();   // wrong check digit
        assertThat(ClientPartyRules.validSaId("8013015009087")).isFalse();   // month 13
        assertThat(ClientPartyRules.validSaId("80010150090")).isFalse();     // too short
        assertThat(ClientPartyRules.validSaId(null)).isFalse();
    }

    @Test @DisplayName("an ID number loses spaces, and is only checked when it is a SA ID")
    void idNumber() {
        assertThat(ClientPartyRules.idNumber("SA_ID", "800101 5009 087")).isEqualTo("8001015009087");
        assertThat(ClientPartyRules.idNumber("PASSPORT", "a1234567")).isEqualTo("A1234567");
        assertThat(ClientPartyRules.idNumber("SA_ID", " ")).isNull();
        assertThatThrownBy(() -> ClientPartyRules.idNumber("SA_ID", "1234567890123")).hasMessageContaining("not valid");
    }

    @Test @DisplayName("only ownership roles hold a percentage, within 0 to 100")
    void percent() {
        assertThat(ClientPartyRules.percent("SHAREHOLDER", bd("25.5"))).isEqualTo(bd("25.5"));
        assertThat(ClientPartyRules.percent("DIRECTOR", null)).isNull();
        assertThatThrownBy(() -> ClientPartyRules.percent("DIRECTOR", bd("10"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ClientPartyRules.percent("SHAREHOLDER", bd("100.1"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ClientPartyRules.percent("SHAREHOLDER", bd("-1"))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test @DisplayName("resigning before being appointed is refused")
    void dates() {
        ClientPartyRules.dates(null, LocalDate.of(2026, 1, 1));
        ClientPartyRules.dates(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 1));
        assertThatThrownBy(() -> ClientPartyRules.dates(LocalDate.of(2026, 2, 1), LocalDate.of(2026, 1, 1))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test @DisplayName("holdings that add up to 100% are fully declared")
    void fullyDeclared() {
        var s = ClientPartyRules.summarise(List.of(h("SHAREHOLDER", "60", true), h("SHAREHOLDER", "40", true), h("DIRECTOR", null, true)));
        assertThat(s.fullyDeclared()).isTrue();
        assertThat(s.hasDirector()).isTrue();
        assertThat(s.directors()).isEqualTo(1);
    }

    @Test @DisplayName("former holders are left out")
    void inactiveIgnored() {
        var s = ClientPartyRules.summarise(List.of(h("SHAREHOLDER", "60", true), h("SHAREHOLDER", "40", false), h("DIRECTOR", null, false)));
        assertThat(s.fullyDeclared()).isFalse();
        assertThat(s.hasDirector()).isFalse();
        assertThat(s.shareholdingDeclared()).isEqualTo(bd("60"));
    }

    @Test @DisplayName("a 5% shareholder with no beneficial owner on file is a gap")
    void beneficialOwnerGap() {
        assertThat(ClientPartyRules.summarise(List.of(h("SHAREHOLDER", "5", true))).beneficialOwnerGap()).isTrue();
        assertThat(ClientPartyRules.summarise(List.of(h("SHAREHOLDER", "4.9", true))).beneficialOwnerGap()).isFalse();
        assertThat(ClientPartyRules.summarise(List.of(h("SHAREHOLDER", "50", true), h("BENEFICIAL_OWNER", "50", true))).beneficialOwnerGap()).isFalse();
        assertThat(ClientPartyRules.summarise(List.of()).beneficialOwnerGap()).isFalse();
    }

    @Test @DisplayName("a shareholding that would pass 100% is caught")
    void exceeds() {
        assertThat(ClientPartyRules.exceedsWhole(bd("60"), bd("40"))).isFalse();
        assertThat(ClientPartyRules.exceedsWhole(bd("60"), bd("40.001"))).isTrue();
        assertThat(ClientPartyRules.exceedsWhole(bd("60"), null)).isFalse();
    }
}
