package za.co.handyflow.platform.events.application.internal;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The CSV writer is the line between a guest-supplied string and the
 * spreadsheet an organiser opens — these tests pin the safety behaviour.
 */
class CsvWriterTest {

    @Test
    void text_prefixesFormulaTriggers_soSpreadsheetsShowThemAsText() {
        assertThat(CsvWriter.text("=HYPERLINK(\"http://evil\",\"x\")")).startsWith("\"'=HYPERLINK");
        assertThat(CsvWriter.text("+27821234567")).isEqualTo("\"'+27821234567\"");
        assertThat(CsvWriter.text("-2+3")).isEqualTo("\"'-2+3\"");
        assertThat(CsvWriter.text("@SUM(A1:A9)")).isEqualTo("\"'@SUM(A1:A9)\"");
        assertThat(CsvWriter.text("\t=1+1")).isEqualTo("\"'\t=1+1\"");
    }

    @Test
    void text_leavesOrdinaryValuesUntouched_apartFromQuoting() {
        assertThat(CsvWriter.text("Thandi Nkosi")).isEqualTo("\"Thandi Nkosi\"");
        assertThat(CsvWriter.text("Gérard O'Brien")).isEqualTo("\"Gérard O'Brien\"");   // apostrophe inside is fine
        assertThat(CsvWriter.text("a=b")).isEqualTo("\"a=b\"");                          // only a LEADING trigger matters
    }

    @Test
    void text_escapesQuotesCommasAndNewlines_perRfc4180() {
        assertThat(CsvWriter.text("say \"hi\", then leave")).isEqualTo("\"say \"\"hi\"\", then leave\"");
        assertThat(CsvWriter.text("line1\nline2")).isEqualTo("\"line1\nline2\"");
    }

    @Test
    void text_nullBecomesAnEmptyCell_andEmptyStringStaysQuoted() {
        assertThat(CsvWriter.text(null)).isEmpty();
        assertThat(CsvWriter.text("")).isEqualTo("\"\"");
    }

    @Test
    void systemValues_areWrittenUnquoted_soSpreadsheetsTreatThemAsNumbersAndDates() {
        assertThat(CsvWriter.num(new BigDecimal("1234.50"))).isEqualTo("1234.50");
        assertThat(CsvWriter.num(new BigDecimal("-100.00"))).isEqualTo("-100.00");    // not text-guarded: system-made
        assertThat(CsvWriter.num((BigDecimal) null)).isEmpty();
        assertThat(CsvWriter.num(42L)).isEqualTo("42");
        assertThat(CsvWriter.bool(true)).isEqualTo("true");
        assertThat(CsvWriter.date(LocalDate.of(2026, 11, 5))).isEqualTo("2026-11-05");
        assertThat(CsvWriter.date(null)).isEmpty();
    }

    @Test
    void time_isShownInSouthAfricanTime() {
        // 10:00 UTC is 12:00 in Johannesburg (UTC+2, no daylight saving)
        assertThat(CsvWriter.time(Instant.parse("2026-11-05T10:00:00Z"))).isEqualTo("2026-11-05 12:00:00");
        assertThat(CsvWriter.time(null)).isEmpty();
    }

    @Test
    void output_startsWithByteOrderMark_usesCrlf_andKeepsAccentsIntact() {
        byte[] bytes = new CsvWriter().row("name", "city").row(CsvWriter.text("Zoë"), CsvWriter.text("Pretoria")).toBytes();
        String s = new String(bytes, StandardCharsets.UTF_8);

        assertThat(bytes[0]).isEqualTo((byte) 0xEF);   // UTF-8 BOM: EF BB BF
        assertThat(bytes[1]).isEqualTo((byte) 0xBB);
        assertThat(bytes[2]).isEqualTo((byte) 0xBF);
        assertThat(s).isEqualTo("\uFEFFname,city\r\n\"Zoë\",\"Pretoria\"\r\n");
    }
}