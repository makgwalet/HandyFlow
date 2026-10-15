package za.co.handyflow.platform.internalaudit.application.internal;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.internalaudit.application.internal.ReportTemplates.FindingText;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ReportTemplatesTest {
    @Test
    void everyTemplateHasAFindingsSection() {
        for (var t : ReportTemplates.all()) {
            assertTrue(t.sections().stream().anyMatch(ReportTemplates.SectionSpec::autoFindings), t.id());
        }
        assertEquals(3, ReportTemplates.all().size());
    }

    @Test
    void templateLookupIsCaseInsensitiveAndRejectsUnknown() {
        assertTrue(ReportTemplates.find("standard").isPresent());
        assertTrue(ReportTemplates.find("nope").isEmpty());
        assertTrue(ReportTemplates.find(null).isEmpty());
    }

    @Test
    void findingsAreWrittenWorstFirstAndOnlyWithWhatIsKnown() {
        String text = ReportTemplates.findingsText(List.of(
                new FindingText("Typos in letters", "LOW", null, "Some letters have typos", null, "Proofread", null),
                new FindingText("Weak approvals", "critical", "Policy 4.2", "No second approver", "Loss risk", "Add approver", "Agreed")));
        assertTrue(text.startsWith("1. Weak approvals [CRITICAL]"));
        assertTrue(text.contains("\n2. Typos in letters [LOW]"));
        assertTrue(text.contains("Management response: Agreed"));
        assertFalse(text.contains("Criteria: null"));
        assertEquals(1, text.split("Criteria:").length - 1);
    }

    @Test
    void noFindingsSaysSo() {
        assertEquals("No findings were raised on this engagement.", ReportTemplates.findingsText(List.of()));
    }

    @Test
    void hashIsStableAndChangesWithAnyEdit() {
        var a = ReportTemplates.contentHash("Report", List.of(new String[]{"Summary", "All good"}, new String[]{"Findings", "None"}));
        var same = ReportTemplates.contentHash("Report", List.of(new String[]{"Summary", "All good"}, new String[]{"Findings", "None"}));
        assertEquals(a, same);
        assertEquals(64, a.length());
        assertTrue(!a.equals(ReportTemplates.contentHash("Report", List.of(new String[]{"Summary", "All good!"}, new String[]{"Findings", "None"}))));
        assertTrue(!a.equals(ReportTemplates.contentHash("Report 2", List.of(new String[]{"Summary", "All good"}, new String[]{"Findings", "None"}))));
        assertTrue(!a.equals(ReportTemplates.contentHash("Report", List.of(new String[]{"Findings", "None"}, new String[]{"Summary", "All good"}))));
    }
}
