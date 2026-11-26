package za.co.handyflow.platform.payrollbureau.domain.rules;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.shared.StatutoryYearRules;
import za.co.handyflow.platform.shared.StatutoryYearRules.Bracket;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RulesFingerprintTest {

    private static BigDecimal d(String s) { return new BigDecimal(s); }

    private static StatutoryYearRules rules(String firstRate, String uifCeiling) {
        return new StatutoryYearRules(2027, LocalDate.of(2026, 3, 1), LocalDate.of(2027, 2, 28),
                List.of(new Bracket(d("0"), d("245100"), d("0"), d(firstRate)),
                        new Bracket(d("245100"), null, d("44118"), d("26"))),
                d("17820"), d("9765"), d("3249"), d("376"), d("376"), d("254"),
                d(uifCeiling), d("0.01"), d("0.01"), d("0.01"), d("500000"), d("430000"), d("0.275"), d("0.8"));
    }

    @Test
    void sameFiguresGiveTheSameFingerprintWhateverTheScale() {
        assertTrue(RulesFingerprint.of(rules("18", "17712")).equals(RulesFingerprint.of(rules("18.00", "17712.00"))));
    }

    @Test
    void anyChangedFigureChangesTheFingerprint() {
        String base = RulesFingerprint.of(rules("18", "17712"));
        assertFalse(base.equals(RulesFingerprint.of(rules("19", "17712"))));
        assertFalse(base.equals(RulesFingerprint.of(rules("18", "18000"))));
    }

    @Test
    void itIsA64CharacterHexString() {
        assertTrue(RulesFingerprint.of(rules("18", "17712")).matches("[0-9a-f]{64}"));
    }
}
