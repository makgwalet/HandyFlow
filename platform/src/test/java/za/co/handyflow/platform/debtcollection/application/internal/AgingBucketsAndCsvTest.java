package za.co.handyflow.platform.debtcollection.application.internal;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AgingBucketsAndCsvTest {

    @Test
    void bucketBoundaries() {
        assertEquals(0, AgingBuckets.indexFor(-10)); // not yet due
        assertEquals(0, AgingBuckets.indexFor(30));
        assertEquals(1, AgingBuckets.indexFor(31));
        assertEquals(1, AgingBuckets.indexFor(60));
        assertEquals(2, AgingBuckets.indexFor(61));
        assertEquals(2, AgingBuckets.indexFor(90));
        assertEquals(3, AgingBuckets.indexFor(91));
        assertEquals(3, AgingBuckets.indexFor(120));
        assertEquals(4, AgingBuckets.indexFor(121));
        assertEquals(AgingBuckets.LABELS.size(), 5);
    }

    @Test
    void csvEscapesAndNeutralisesFormulas() {
        assertEquals("plain", CsvSupport.cell("plain"));
        assertEquals("\"a,b\"", CsvSupport.cell("a,b"));
        assertEquals("\"say \"\"hi\"\"\"", CsvSupport.cell("say \"hi\""));
        assertEquals("'=SUM(A1)", CsvSupport.cell("=SUM(A1)"));
        assertEquals("'@cmd", CsvSupport.cell("@cmd"));
        assertEquals("-5", CsvSupport.cell(-5));                       // numbers are not guarded
        assertEquals("1200.50", CsvSupport.cell(new BigDecimal("1200.50")));
        assertEquals("", CsvSupport.cell(null));
    }
}
