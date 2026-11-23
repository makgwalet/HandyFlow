package za.co.handyflow.platform.agriculture.domain.rules;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.agriculture.domain.rules.AgRegisterRules.Herd;
import za.co.handyflow.platform.agriculture.domain.rules.AgRegisterRules.Spray;
import za.co.handyflow.platform.agriculture.domain.rules.AgRegisterRules.Treatment;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AgRegisterRulesTest {

    private static String[] lines(String csv) { return csv.substring(1).split("\r\n"); }   // drop the byte order mark

    @Test void theFileStartsWithAByteOrderMarkAndEveryLineEndsWithCrLf() {
        String csv = AgRegisterRules.herdCsv("Rietvlei", "ZK 1", null, List.of());
        assertTrue(csv.startsWith(AgCsv.BOM));
        assertTrue(csv.endsWith("\r\n"));
        assertEquals(2, lines(csv).length, "title line and header line");
    }

    @Test void herdRowsHaveOneCellPerHeadingAndShowAnimalsAndGroups() {
        List<Herd> rows = List.of(
                new Herd("Animal", "AN-001", "Bessie", "Cattle", "Bonsmara", "FEMALE", 1, LocalDate.of(2022, 3, 12), true, "ACTIVE", true,
                        "PURCHASED", LocalDate.of(2023, 1, 5), new BigDecimal("8500.00"), "AN-100", null),
                new Herd("Group", "B-2", null, "Sheep", "Dorper", null, 12, LocalDate.of(2026, 5, 1), false, "ACTIVE", false, "BORN_ON_FARM", null, null, null, null));
        String[] l = lines(AgRegisterRules.herdCsv("Rietvlei", "ZK 1", "Ear", rows));
        assertEquals(4, l.length);
        assertEquals("Herd register,Rietvlei,Brand mark,ZK 1,Ear mark,Ear", l[0]);
        assertEquals(l[1].split(",", -1).length, l[2].split(",", -1).length);
        assertEquals(l[1].split(",", -1).length, l[3].split(",", -1).length);
        assertEquals("Animal,AN-001,Bessie,Cattle,Bonsmara,FEMALE,1,2022-03-12,Yes,ACTIVE,Yes,PURCHASED,2023-01-05,8500,AN-100,", l[2]);
        assertEquals("Group,B-2,,Sheep,Dorper,,12,2026-05-01,No,ACTIVE,No,BORN_ON_FARM,,,,", l[3]);
    }

    @Test void aMissingBirthDateLeavesTheEstimatedColumnBlank() {
        Herd h = new Herd("Animal", "A", null, null, null, null, 1, null, true, "ACTIVE", false, null, null, null, null, null);
        assertTrue(lines(AgRegisterRules.herdCsv("F", null, null, List.of(h)))[2].startsWith("Animal,A,,,,,1,,,ACTIVE,No,"));
    }

    @Test void treatmentRowsCarryTheWithdrawalEndDate() {
        Treatment t = new Treatment(LocalDate.of(2026, 12, 1), "Animal AN-001", "TREATMENT", "Foot rot", "Terramycin LA", "10 ml", "J Smith", "Dr Vet", 14, "COMPLETED", null);
        Treatment none = new Treatment(LocalDate.of(2026, 12, 2), "Batch B-2", "VACCINATION", "Pulpy kidney", "Multivax", null, null, null, null, "COMPLETED", "ok");
        String[] l = lines(AgRegisterRules.treatmentCsv("Rietvlei", List.of(t, none)));
        assertEquals("2026-12-01,Animal AN-001,TREATMENT,Foot rot,Terramycin LA,10 ml,J Smith,Dr Vet,14,2026-12-15,COMPLETED,", l[2]);
        assertEquals("2026-12-02,Batch B-2,VACCINATION,Pulpy kidney,Multivax,,,,,,COMPLETED,ok", l[3]);
    }

    @Test void sprayRowsCarryTheHarvestClearDate() {
        Spray s = new Spray(LocalDate.of(2026, 12, 1), "Maize 2026/27", "PESTICIDE", "Karate", new BigDecimal("2.50"), "l", "Boom", "T Worker", "calm", 21, null);
        Spray plain = new Spray(LocalDate.of(2026, 12, 2), "Maize 2026/27", "FERTILISER", null, new BigDecimal("50"), "kg", null, null, null, null, null);
        String[] l = lines(AgRegisterRules.sprayCsv("Rietvlei", List.of(s, plain)));
        assertEquals("2026-12-01,Maize 2026/27,PESTICIDE,Karate,2.5,l,Boom,T Worker,calm,21,2026-12-22,", l[2]);
        assertEquals("2026-12-02,Maize 2026/27,FERTILISER,,50,kg,,,,,,", l[3]);
    }

    @Test void hostileTextInARecordCannotBecomeAFormulaOrBreakTheLayout() {
        Treatment t = new Treatment(LocalDate.of(2026, 12, 1), "=cmd|' /C calc'!A0", "TREATMENT", "a,b\nc", "@x", null, null, null, null, "COMPLETED", "-1+1");
        String csv = AgRegisterRules.treatmentCsv("F", List.of(t));
        assertTrue(csv.contains(",'=cmd|' /C calc'!A0,"), csv);
        assertTrue(csv.contains("\"a,b\nc\""), csv);
        assertTrue(csv.contains(",'@x,"), csv);
        assertTrue(csv.contains("'-1+1"), csv);
    }
}
