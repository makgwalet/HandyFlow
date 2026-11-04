package za.co.handyflow.platform.facilities.domain;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.facilities.domain.model.SiteAddressRules;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SiteAddressRulesTest {

    private static Map<String, String> addr(String province, String postal) {
        Map<String, String> m = new HashMap<>();
        m.put("city", "Midrand");
        m.put("province", province);
        m.put("postalCode", postal);
        return m;
    }

    @Test
    void acceptsFourDigitsAndFixesTheProvinceSpelling() {
        var r = SiteAddressRules.check(addr("gauteng", " 2196 "));
        assertEquals("Gauteng", r.get("province"));
        assertEquals("2196", r.get("postalCode"));
        assertEquals("Western Cape", SiteAddressRules.check(addr("WC", "8001")).get("province"));
        assertEquals("KwaZulu-Natal", SiteAddressRules.check(addr("kwazulu-natal", null)).get("province"));
    }

    @Test
    void refusesLettersAndWrongLengthInThePostalCode() {
        assertThrows(IllegalArgumentException.class, () -> SiteAddressRules.check(addr("Gauteng", "21A6")));
        assertThrows(IllegalArgumentException.class, () -> SiteAddressRules.check(addr("Gauteng", "219")));
        assertThrows(IllegalArgumentException.class, () -> SiteAddressRules.check(addr("Gauteng", "21966")));
    }

    @Test
    void refusesAProvinceThatIsNotOneOfTheNine() {
        var e = assertThrows(IllegalArgumentException.class, () -> SiteAddressRules.check(addr("Narnia", "2196")));
        assertEquals("Choose one of the nine provinces, for example Gauteng or Western Cape.", e.getMessage());
    }

    @Test
    void emptyProvinceAndPostalCodeAreLeftOut() {
        var r = SiteAddressRules.check(addr("  ", ""));
        assertNull(r.get("province"));
        assertNull(r.get("postalCode"));
        assertNull(SiteAddressRules.check(null));
    }
}
