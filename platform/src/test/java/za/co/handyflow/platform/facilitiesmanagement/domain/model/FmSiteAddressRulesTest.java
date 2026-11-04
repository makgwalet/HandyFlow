package za.co.handyflow.platform.facilitiesmanagement.domain.model;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FmSiteAddressRulesTest {

    @Test
    void acceptsFourDigitPostalCodeAndFixesProvinceSpelling() {
        Map<String, String> out = FmSiteAddressRules.check(Map.of("postalCode", " 2196 ", "province", "gauteng", "city", "Sandton"));
        assertThat(out).containsEntry("postalCode", "2196").containsEntry("province", "Gauteng").containsEntry("city", "Sandton");
    }

    @Test
    void acceptsProvinceCodes() {
        assertThat(FmSiteAddressRules.check(Map.of("province", "KZN"))).containsEntry("province", "KwaZulu-Natal");
    }

    @Test
    void refusesBadPostalCodeAndUnknownProvince() {
        assertThatThrownBy(() -> FmSiteAddressRules.check(Map.of("postalCode", "21"))).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("four digits");
        assertThatThrownBy(() -> FmSiteAddressRules.check(Map.of("province", "Atlantis"))).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("nine provinces");
    }

    @Test
    void dropsBlankFieldsAndPassesNullThrough() {
        assertThat(FmSiteAddressRules.check(Map.of("postalCode", " ", "province", ""))).isEmpty();
        assertThat(FmSiteAddressRules.check(null)).isNull();
    }
}
