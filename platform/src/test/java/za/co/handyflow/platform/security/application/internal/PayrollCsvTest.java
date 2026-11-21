package za.co.handyflow.platform.security.application.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

class PayrollCsvTest {

    @Test @DisplayName("money has a decimal point whatever the server locale")
    void moneyIgnoresLocale() {
        Locale old = Locale.getDefault();
        try {
            Locale.setDefault(new Locale("en", "ZA"));
            assertEquals("1234.56", PayrollCsv.money(123456));
            assertEquals("0.05", PayrollCsv.money(5));
            assertEquals("0.00", PayrollCsv.money(0));
            assertEquals("-12.30", PayrollCsv.money(-1230));
            assertEquals("7.50", PayrollCsv.hours(new BigDecimal("7.5")));
            assertEquals("0.00", PayrollCsv.hours(null));
        } finally { Locale.setDefault(old); }
    }

    @Test @DisplayName("text is always quoted and quotes are doubled, so a name cannot break the row")
    void quoting() {
        assertEquals("\"Thabo Mokoena\"", PayrollCsv.text("Thabo Mokoena"));
        assertEquals("\"Sipho \"\"Biggie\"\" Dlamini\"", PayrollCsv.text("Sipho \"Biggie\" Dlamini"));
        assertEquals("\"Smith, John\"", PayrollCsv.text("Smith, John"));
        assertEquals("\"\"", PayrollCsv.text(null));
        assertEquals("\"line one line two\"", PayrollCsv.text("line one\nline two"));
    }

    @Test @DisplayName("a name that starts like a spreadsheet formula stays text")
    void formulaInjection() {
        assertEquals("\"'=HYPERLINK(\"\"http://x\"\")\"", PayrollCsv.text("=HYPERLINK(\"http://x\")"));
        assertEquals("\"'+1\"", PayrollCsv.text("+1"));
        assertEquals("\"'-1\"", PayrollCsv.text("-1"));
        assertEquals("\"'@SUM(A1)\"", PayrollCsv.text("@SUM(A1)"));
        assertEquals("\"Mark = Jones\"", PayrollCsv.text("Mark = Jones"), "only a leading character matters");
    }
}
