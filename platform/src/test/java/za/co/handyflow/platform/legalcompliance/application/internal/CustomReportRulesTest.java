package za.co.handyflow.platform.legalcompliance.application.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.legalcompliance.application.internal.CustomReportRules.*;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CustomReportRulesTest {

    private static final List<String> COLS = List.of("Reference", "Title", "Owner", "Score", "Due");
    private static final List<List<String>> ROWS = List.of(
            List.of("R-1", "Data breach plan", "Anna", "9", "2026-03-01"),
            List.of("R-2", "Supplier review", "Ben", "10", "2026-01-15"),
            List.of("R-3", "Policy refresh", "anna", "2", ""),
            List.of("R-4", "Licence renewal", "", "", "2026-02-10"));

    private static Def def(List<String> cols, List<Filter> fs, String sort, boolean desc) { return new Def("RISK_REGISTER", cols, fs, sort, desc); }
    private static Filter f(String c, Op op, String v) { return new Filter(c, op, v); }
    private static List<String> refs(Result r) { return r.rows().stream().map(x -> x.get(0)).toList(); }

    @Test @DisplayName("A valid definition has no problem; each kind of invalid one says what is wrong")
    void validation() {
        assertNull(CustomReportRules.problem(def(List.of("Title", "Score"), List.of(f("Owner", Op.EQUALS, "Anna")), "Score", true), COLS));
        assertNotNull(CustomReportRules.problem(null, COLS));
        assertNotNull(CustomReportRules.problem(new Def(" ", List.of("Title"), List.of(), null, false), COLS));
        assertNotNull(CustomReportRules.problem(def(List.of(), List.of(), null, false), COLS));
        assertNotNull(CustomReportRules.problem(def(List.of("Title", "Title"), List.of(), null, false), COLS));
        assertTrue(CustomReportRules.problem(def(List.of("Secret"), List.of(), null, false), COLS).contains("Secret"));
        assertNotNull(CustomReportRules.problem(def(List.of("Title"), List.of(f("Nope", Op.EQUALS, "x")), null, false), COLS));
        assertNotNull(CustomReportRules.problem(def(List.of("Title"), List.of(f("Title", Op.EQUALS, " ")), null, false), COLS), "a test that needs a value needs one");
        assertNull(CustomReportRules.problem(def(List.of("Title"), List.of(f("Title", Op.EMPTY, null)), null, false), COLS), "EMPTY needs none");
        assertNotNull(CustomReportRules.problem(def(List.of("Title"), List.of(new Filter("Title", null, "x")), null, false), COLS));
        assertNotNull(CustomReportRules.problem(def(List.of("Title"), List.of(f("Title", Op.CONTAINS, "x".repeat(201))), null, false), COLS));
        assertNotNull(CustomReportRules.problem(def(List.of("Title"), List.of(), "Nope", false), COLS));
        assertNull(CustomReportRules.problem(def(List.of("Title"), List.of(), " ", false), COLS), "a blank sort means none");
        assertNull(CustomReportRules.problem(def(List.of("Title"), List.of(), "Score", false), COLS), "sorting by a column that is not shown is fine");
        List<Filter> eleven = java.util.Collections.nCopies(11, f("Title", Op.CONTAINS, "a"));
        assertNotNull(CustomReportRules.problem(def(List.of("Title"), eleven, null, false), COLS));
        assertNull(CustomReportRules.problem(def(List.of("Title"), eleven.subList(0, 10), null, false), COLS));
    }

    @Test @DisplayName("Name and description limits")
    void names() {
        assertNull(CustomReportRules.nameProblem("Top risks", null));
        assertNotNull(CustomReportRules.nameProblem("ab", null));
        assertNotNull(CustomReportRules.nameProblem(null, null));
        assertNotNull(CustomReportRules.nameProblem("x".repeat(121), null));
        assertNotNull(CustomReportRules.nameProblem("Top risks", "x".repeat(501)));
    }

    @Test @DisplayName("Columns are shown in the chosen order and only the chosen ones")
    void columnsAndOrder() {
        Result r = CustomReportRules.apply(def(List.of("Score", "Reference"), List.of(), null, false), COLS, ROWS);
        assertEquals(List.of("Score", "Reference"), r.columns());
        assertEquals(List.of("9", "R-1"), r.rows().get(0));
        assertEquals(4, r.rows().size());
    }

    @Test @DisplayName("Filters are applied to every column of the standard report, shown or not, and all must hold")
    void filters() {
        assertEquals(List.of("R-1", "R-3"), refs(CustomReportRules.apply(def(List.of("Reference"), List.of(f("Owner", Op.EQUALS, "ANNA")), null, false), COLS, ROWS)), "equals ignores case");
        assertEquals(List.of("R-2", "R-4"), refs(CustomReportRules.apply(def(List.of("Reference"), List.of(f("Owner", Op.NOT_EQUALS, "anna")), null, false), COLS, ROWS)));
        assertEquals(List.of("R-2"), refs(CustomReportRules.apply(def(List.of("Reference"), List.of(f("Title", Op.CONTAINS, "SUPPLIER")), null, false), COLS, ROWS)));
        assertEquals(List.of("R-1", "R-3", "R-4"), refs(CustomReportRules.apply(def(List.of("Reference"), List.of(f("Title", Op.NOT_CONTAINS, "supplier")), null, false), COLS, ROWS)));
        assertEquals(List.of("R-3"), refs(CustomReportRules.apply(def(List.of("Reference"), List.of(f("Title", Op.STARTS_WITH, "pol")), null, false), COLS, ROWS)));
        assertEquals(List.of("R-4"), refs(CustomReportRules.apply(def(List.of("Reference"), List.of(f("Owner", Op.EMPTY, null)), null, false), COLS, ROWS)));
        assertEquals(List.of("R-1", "R-2", "R-3"), refs(CustomReportRules.apply(def(List.of("Reference"), List.of(f("Owner", Op.NOT_EMPTY, null)), null, false), COLS, ROWS)));
        assertEquals(List.of("R-3"), refs(CustomReportRules.apply(def(List.of("Reference"), List.of(f("Owner", Op.EQUALS, "anna"), f("Score", Op.LT, "5")), null, false), COLS, ROWS)), "both filters must hold");
        assertTrue(CustomReportRules.apply(def(List.of("Reference"), List.of(f("Owner", Op.EQUALS, "Zed")), null, false), COLS, ROWS).rows().isEmpty());
    }

    @Test @DisplayName("Greater and less than compare numbers as numbers (10 is more than 9) and ISO dates as dates, and never match a value that does not parse")
    void ordering() {
        assertEquals(List.of("R-2"), refs(CustomReportRules.apply(def(List.of("Reference"), List.of(f("Score", Op.GT, "9")), null, false), COLS, ROWS)));
        assertEquals(List.of("R-1", "R-2"), refs(CustomReportRules.apply(def(List.of("Reference"), List.of(f("Score", Op.GTE, "9")), null, false), COLS, ROWS)));
        assertEquals(List.of("R-3"), refs(CustomReportRules.apply(def(List.of("Reference"), List.of(f("Score", Op.LTE, "2")), null, false), COLS, ROWS)), "a blank score is not 'less than' anything");
        assertEquals(List.of("R-1", "R-4"), refs(CustomReportRules.apply(def(List.of("Reference"), List.of(f("Due", Op.GT, "2026-02-01")), null, false), COLS, ROWS)));
        assertEquals(List.of("R-2"), refs(CustomReportRules.apply(def(List.of("Reference"), List.of(f("Due", Op.LT, "2026-02-01")), null, false), COLS, ROWS)));
        assertTrue(CustomReportRules.apply(def(List.of("Reference"), List.of(f("Title", Op.GT, "5")), null, false), COLS, ROWS).rows().isEmpty(), "text against a number never matches");
        assertEquals(List.of("R-1"), refs(CustomReportRules.apply(def(List.of("Reference"), List.of(f("Score", Op.EQUALS, "9.0")), null, false), COLS, ROWS)), "9 equals 9.0");
    }

    @Test @DisplayName("Sorting: numbers numerically, dates as dates, text ignoring case, blanks always last, stable on ties")
    void sorting() {
        assertEquals(List.of("R-3", "R-1", "R-2", "R-4"), refs(CustomReportRules.apply(def(List.of("Reference"), List.of(), "Score", false), COLS, ROWS)));
        assertEquals(List.of("R-2", "R-1", "R-3", "R-4"), refs(CustomReportRules.apply(def(List.of("Reference"), List.of(), "Score", true), COLS, ROWS)), "blank stays last when descending");
        assertEquals(List.of("R-2", "R-4", "R-1", "R-3"), refs(CustomReportRules.apply(def(List.of("Reference"), List.of(), "Due", false), COLS, ROWS)));
        assertEquals(List.of("R-1", "R-3", "R-2", "R-4"), refs(CustomReportRules.apply(def(List.of("Reference"), List.of(), "Owner", false), COLS, ROWS)), "Anna and anna tie, so input order is kept");
        assertEquals(List.of("R-1", "R-2", "R-3", "R-4"), refs(CustomReportRules.apply(def(List.of("Reference"), List.of(), null, false), COLS, ROWS)), "no sort keeps the standard order");
    }

    @Test @DisplayName("Short or null cells never throw")
    void ragged() {
        List<List<String>> odd = new java.util.ArrayList<>();
        odd.add(java.util.Arrays.asList("R-9", null));
        odd.add(List.of("R-8"));
        Result r = CustomReportRules.apply(def(List.of("Reference", "Title"), List.of(f("Score", Op.EMPTY, null)), "Title", false), COLS, odd);
        assertEquals(2, r.rows().size());
        assertEquals(List.of("R-9", ""), r.rows().get(0));
    }

    @Test @DisplayName("Blanks go last in both directions even when they come first in the data")
    void blanksLast() {
        List<List<String>> rows = List.of(List.of("B", "", "", "", ""), List.of("A", "", "", "5", ""), List.of("C", "", "", "3", ""));
        assertEquals(List.of("C", "A", "B"), refs(CustomReportRules.apply(def(List.of("Reference"), List.of(), "Score", false), COLS, rows)));
        assertEquals(List.of("A", "C", "B"), refs(CustomReportRules.apply(def(List.of("Reference"), List.of(), "Score", true), COLS, rows)));
    }
}
