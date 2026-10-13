package za.co.handyflow.platform.clinic.application.internal;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.clinic.dto.GrowthDtos.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class GrowthExportRulesTest {
    private static GrowthChart chart(String banner, Double z) {
        var m = new MeasureChart("WEIGHT", "Weight", "kg", banner,
                List.of(new Measured(UUID.randomUUID(), Instant.parse("2026-09-30T23:30:00Z"), 14.25, new BigDecimal("9.80"), z, z == null ? null : 41.0)), null);
        return new GrowthChart(UUID.randomUUID(), "FEMALE", 14.3, List.of("Sex at birth is used for the curves."), List.of(m));
    }

    @Test void cellsAreQuotedWhenTheyNeedIt() {
        assertEquals("plain", GrowthExportRules.cell("plain"));
        assertEquals("\"a,b\"", GrowthExportRules.cell("a,b"));
        assertEquals("\"say \"\"hi\"\"\"", GrowthExportRules.cell("say \"hi\""));
        assertEquals("", GrowthExportRules.cell(null));
    }
    @Test void formulaStartersAreDefused() {
        assertEquals("'=SUM(A1)", GrowthExportRules.cell("=SUM(A1)"));
        assertEquals("'+1", GrowthExportRules.cell("+1"));
        assertEquals("'-1", GrowthExportRules.cell("-1"));
        assertEquals("'@x", GrowthExportRules.cell("@x"));
    }
    @Test void aPatientNameCannotInjectAFormula() {
        String csv = GrowthExportRules.csv("=HYPERLINK(\"http://x\")", chart(null, 0.5), "2026-10-13");
        assertTrue(csv.contains("Patient,\"'=HYPERLINK(\"\"http://x\"\")\""));
    }
    @Test void measurementsAreDatedInSouthAfricanTime() {
        String csv = GrowthExportRules.csv("Thandi Nkosi", chart(null, 0.5), "2026-10-13");
        assertTrue(csv.contains("Weight,kg,2026-10-01,14.3,9.80,0.50,41.0"));   // 23:30 UTC is already the next day in SAST
    }
    @Test void anUnapprovedReferenceLeavesZScoresEmptyAndSaysSo() {
        String csv = GrowthExportRules.csv("Thandi Nkosi", chart("DATA NOT CLINICALLY APPROVED", null), "2026-10-13");
        assertTrue(csv.contains("Weight,kg,2026-10-01,14.3,9.80,,"));
        assertTrue(csv.contains("Note,Weight: DATA NOT CLINICALLY APPROVED"));
    }
    @Test void headerAndNotesTravelWithTheData() {
        String csv = GrowthExportRules.csv("Thandi Nkosi", chart(null, 0.5), "2026-10-13");
        assertTrue(csv.startsWith("Growth chart export\r\nPatient,Thandi Nkosi\r\nSex at birth,FEMALE\r\n"));
        assertTrue(csv.contains("Exported,2026-10-13"));
        assertTrue(csv.contains("Note,Sex at birth is used for the curves."));
    }
    @Test void fileNamesAreSafe() {
        assertEquals("growth-thandi-nkosi-2026-10-13.csv", GrowthExportRules.fileName("Thandi  Nkosi", "2026-10-13"));
        assertEquals("growth-patient-2026-10-13.csv", GrowthExportRules.fileName("../../", "2026-10-13"));
        assertEquals("growth-patient-2026-10-13.csv", GrowthExportRules.fileName(null, "2026-10-13"));
    }
}
