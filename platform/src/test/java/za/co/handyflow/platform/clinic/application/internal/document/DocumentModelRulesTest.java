package za.co.handyflow.platform.clinic.application.internal.document;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.clinic.application.internal.document.DocumentModel.*;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DocumentModelRulesTest {

    private static DocumentModel with(Block... blocks) {
        return new DocumentModel("T", null, List.of(blocks), null, false);
    }

    @Test void everyReferenceDocumentIsValid() {
        var all = ReferenceDocuments.all();
        assertEquals(7, all.size());
        all.forEach((name, m) -> assertEquals(List.of(), DocumentModelRules.problems(m), name));
    }

    @Test void aDocumentNeedsATitleAndContent() {
        assertFalse(DocumentModelRules.problems(new DocumentModel(" ", null, List.of(), null, false)).isEmpty());
        assertEquals(2, DocumentModelRules.problems(new DocumentModel(null, null, null, null, false)).size());
        assertFalse(DocumentModelRules.problems(null).isEmpty());
    }

    @Test void aTableMustMatchItsHeader() {
        var bad = with(new Grid(List.of("A", "B"), List.of(1d), List.of(List.of("x", "y"))));
        assertTrue(DocumentModelRules.problems(bad).stream().anyMatch(s -> s.contains("one width")));
        var rowBad = with(new Grid(List.of("A", "B"), List.of(1d, 1d), List.of(List.of("x"))));
        assertTrue(DocumentModelRules.problems(rowBad).stream().anyMatch(s -> s.contains("cells")));
        var zero = with(new Grid(List.of("A"), List.of(0d), List.of()));
        assertTrue(DocumentModelRules.problems(zero).stream().anyMatch(s -> s.contains("above zero")));
    }

    @Test void headingsAndSignaturesAreChecked() {
        assertFalse(DocumentModelRules.problems(with(new Heading(4, "x"))).isEmpty());
        assertFalse(DocumentModelRules.problems(with(new Heading(1, " "))).isEmpty());
        assertFalse(DocumentModelRules.problems(with(new Signature("Regards", "", null, null))).isEmpty());
        assertFalse(DocumentModelRules.problems(with(new KeyValues(List.<String[]>of(new String[]{"", "v"})))).isEmpty());
    }

    @Test void blankValuesPrintAsADashAndNeverDisappear() {
        var m = with(new KeyValues(List.<String[]>of(new String[]{"Employer", null}, new String[]{"Notes", "  "})));
        String t = DocumentModelRules.text(m);
        assertTrue(t.contains("Employer: –"));
        assertTrue(t.contains("Notes: –"));
    }

    @Test void controlCharactersAreRemovedButTextIsKept() {
        assertEquals("a b", DocumentModelRules.clean("a\tb"));
        assertEquals("ab", DocumentModelRules.clean("a\u0000b\u0007"));
        assertEquals("Ünïcödé “x” R1 234,56", DocumentModelRules.clean(" Ünïcödé “x” R1 234,56 "));
        assertEquals("", DocumentModelRules.clean(null));
    }

    @Test void textIsInReadingOrder() {
        String t = DocumentModelRules.text(ReferenceDocuments.all().get("sick-note-branch"));
        assertTrue(t.indexOf("Northside Branch") < t.indexOf("Sick note"));
        assertTrue(t.indexOf("Sick note") < t.indexOf("Patient: Test Patient One"));
        assertTrue(t.indexOf("Patient:") < t.indexOf("Dr. Sample Doctor"));
        assertTrue(t.trim().endsWith("Synthetic reference document"));
    }

    @Test void theLongTableRunsOverSeveralPages() {
        var t = (Grid) ReferenceDocuments.all().get("medical-report-tables").blocks().stream().filter(b -> b instanceof Grid).findFirst().orElseThrow();
        assertTrue(t.rows().size() >= 40, "enough rows to need a repeating header");
    }
}
