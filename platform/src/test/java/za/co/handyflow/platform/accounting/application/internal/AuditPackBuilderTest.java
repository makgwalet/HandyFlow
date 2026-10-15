package za.co.handyflow.platform.accounting.application.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.accounting.application.internal.AuditPackBuilder.Table;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.*;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.*;

class AuditPackBuilderTest {

    private static Table table(String... cells) {
        return new Table("01_test.csv", "A test schedule", List.of("a", "b"), List.of(Arrays.asList(cells)));
    }

    private AuditPackBuilder.Pack pack(Instant at, String by, Table... tables) {
        return AuditPackBuilder.build(Map.of("periodFrom", "2025-03-01"), List.of(tables), List.of("Creditors"), List.of("note"), at, by);
    }

    @Test
    void sameDataGivesTheSameFingerprintWhateverTheTimeOrPerson() {
        var a = pack(Instant.parse("2026-10-15T10:00:00Z"), "Thandi", table("1", "2"));
        var b = pack(Instant.parse("2027-02-01T08:30:00Z"), "Someone else", table("1", "2"));
        assertEquals(a.packSha256(), b.packSha256());
        assertEquals(a.files().get(0).sha256(), b.files().get(0).sha256());
    }

    @Test
    void anyChangeToTheDataChangesTheFingerprint() {
        var a = pack(Instant.now(), "x", table("1", "2"));
        var b = pack(Instant.now(), "x", table("1", "3"));
        assertNotEquals(a.packSha256(), b.packSha256());
    }

    @Test
    void zipHoldsEveryScheduleAndTheManifest() throws Exception {
        var p = pack(Instant.now(), "x", table("1", "2"), new Table("02_other.csv", "Another", List.of("c"), List.of()));
        List<String> names = new ArrayList<>();
        try (var zin = new ZipInputStream(new ByteArrayInputStream(p.zip()))) {
            for (var e = zin.getNextEntry(); e != null; e = zin.getNextEntry()) names.add(e.getName());
        }
        assertEquals(List.of("01_test.csv", "02_other.csv", "manifest.json"), names);
    }

    @Test
    void manifestListsHashesRowCountsAndWhatIsMissing() throws Exception {
        var p = pack(Instant.parse("2026-10-15T10:00:00Z"), "Thandi", table("1", "2"));
        var m = new ObjectMapper().readTree(p.manifestJson());
        assertEquals("Thandi", m.get("generatedBy").asText());
        assertEquals(p.packSha256(), m.get("packSha256").asText());
        assertEquals(1, m.get("files").get(0).get("rows").asInt());
        assertEquals("Creditors", m.get("notIncluded").get(0).asText());
        assertEquals(64, m.get("files").get(0).get("sha256").asText().length());
    }

    @Test
    void cellsThatLookLikeFormulasStayText() {
        assertEquals("'=SUM(A1)", AuditPackBuilder.cell("=SUM(A1)"));
        assertEquals("'@cmd", AuditPackBuilder.cell("@cmd"));
        assertEquals("'+27 82 000", AuditPackBuilder.cell("+27 82 000"));
    }

    @Test
    void numbersAndOrdinaryTextAreLeftAlone() {
        assertEquals("-1250.50", AuditPackBuilder.cell("-1250.50"));
        assertEquals("1250.50", AuditPackBuilder.cell("1250.50"));
        assertEquals("Rent", AuditPackBuilder.cell("Rent"));
        assertEquals("", AuditPackBuilder.cell(null));
    }

    @Test
    void commasQuotesAndLineBreaksAreEscaped() {
        assertEquals("\"Smith, J\"", AuditPackBuilder.cell("Smith, J"));
        assertEquals("\"say \"\"hi\"\"\"", AuditPackBuilder.cell("say \"hi\""));
        assertEquals("\"a\nb\"", AuditPackBuilder.cell("a\nb"));
    }
}
