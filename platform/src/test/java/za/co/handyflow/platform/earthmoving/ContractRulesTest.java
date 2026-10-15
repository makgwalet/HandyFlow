package za.co.handyflow.platform.earthmoving;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.earthmoving.domain.model.ContractRules;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class ContractRulesTest {
    static final LocalDate D1 = LocalDate.of(2026, 11, 1);
    static BigDecimal bd(String s) { return new BigDecimal(s); }

    @Test void valueIsDailyRateTimesDaysCountingBothEnds() {
        assertEquals(bd("10000.00"), ContractRules.expectedValue(bd("1000"), D1, D1.plusDays(9)));
        assertEquals(bd("1000.00"), ContractRules.expectedValue(bd("1000"), D1, D1));
    }
    @Test void valueIsUnknownWithoutARateOrProperDates() {
        assertNull(ContractRules.expectedValue(null, D1, D1));
        assertNull(ContractRules.expectedValue(bd("1000"), null, D1));
        assertNull(ContractRules.expectedValue(bd("1000"), D1, null));
        assertNull(ContractRules.expectedValue(bd("1000"), D1, D1.minusDays(1)));
    }
    @Test void thresholdMustBeSensible() {
        assertNull(ContractRules.checkThreshold(null));
        assertEquals(bd("5000.00"), ContractRules.checkThreshold(bd("5000")));
        assertEquals(bd("0.00"), ContractRules.checkThreshold(BigDecimal.ZERO));
        assertThrows(IllegalArgumentException.class, () -> ContractRules.checkThreshold(bd("-1")));
        assertThrows(IllegalArgumentException.class, () -> ContractRules.checkThreshold(bd("2000000000")));
    }
    @Test void aContractIsExpectedAtOrAboveTheThresholdOnly() {
        assertTrue(ContractRules.isExpected(bd("5000"), bd("5000")));
        assertFalse(ContractRules.isExpected(bd("5000"), bd("4999.99")));
        assertFalse(ContractRules.isExpected(null, bd("999999")));
        assertFalse(ContractRules.isExpected(bd("5000"), null));
    }
    @Test void warnsOnlyWhenAContractIsExpectedAndNotSigned() {
        assertNull(ContractRules.warning(bd("5000"), bd("100"), null, null));
        assertTrue(ContractRules.warning(bd("5000"), bd("6000"), null, null).contains("no contract is linked"));
        assertNull(ContractRules.warning(bd("5000"), bd("6000"), "CON-1", "SIGNED"));
        assertTrue(ContractRules.warning(bd("5000"), bd("6000"), "CON-1", "SENT").contains("sent but not signed yet"));
        assertTrue(ContractRules.warning(bd("5000"), bd("6000"), "CON-1", "UNDER_REVIEW").contains("under review"));
        assertTrue(ContractRules.warning(bd("5000"), bd("6000"), "CON-1", "WEIRD_THING").contains("weird thing"));
    }
    @Test void addendumTextIsEscaped() {
        String body = ContractRules.addendumBody("CON-1", "D9 <b>", D1, D1.plusDays(5), "Rain & <script>");
        assertTrue(body.contains("D9 &lt;b&gt;"));
        assertTrue(body.contains("Rain &amp; &lt;script&gt;"));
        assertFalse(body.contains("<script>"));
        assertTrue(body.contains(D1.plusDays(5).toString()));
    }
}
