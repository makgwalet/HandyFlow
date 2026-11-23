package za.co.handyflow.platform.agriculture.domain.rules;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.agriculture.domain.rules.AgSaleDocumentRules.Document;
import za.co.handyflow.platform.agriculture.domain.rules.AgSaleDocumentRules.Line;
import za.co.handyflow.platform.agriculture.domain.rules.AgSaleDocumentRules.Party;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AgSaleDocumentRulesTest {

    private static final UUID ID = UUID.fromString("1a2b3c4d-0000-4000-8000-000000000001");
    private static final LocalDate DAY = LocalDate.of(2026, 11, 23);
    private static final List<Line> LINES = List.of(new Line("AN-001", "Cattle, female, Bonsmara", 1), new Line("Batch B-2", "Sheep, Dorper", 12));

    private static Document full(String type) {
        return AgSaleDocumentRules.build(ID, DAY, type, "Rietvlei", "REG-1", "ZK 4821", "Swallow-tail", "MR/77",
                new Party("J Farmer", null, "Plot 4, Bethlehem"), new Party("P Buyer", "8001015009087", "1 Main St"), "Abattoir, Harrismith", "CA 123-456", LINES);
    }

    @Test void onlyASaleOrTransferOutGetsADocument() {
        AgSaleDocumentRules.requireDocumentable("SALE");
        AgSaleDocumentRules.requireDocumentable("TRANSFER_OUT");
        for (String t : new String[] {"AREA_TRANSFER", "FARM_TRANSFER", "", "sale"})
            assertThrows(IllegalStateException.class, () -> AgSaleDocumentRules.requireDocumentable(t), t);
        assertThrows(IllegalStateException.class, () -> full("AREA_TRANSFER"));
    }

    @Test void theNumberCanBeFoundAgainFromTheRecord() {
        assertEquals("S6-1A2B3C4D-20261123", AgSaleDocumentRules.number(ID, DAY));
        assertEquals("S6-1A2B3C4D-20261123", full("SALE").number());
    }

    @Test void aCompleteSaleHasNoWarningsAndTheRightTotals() {
        Document d = full("SALE");
        assertTrue(d.warnings().isEmpty(), d.warnings().toString());
        assertEquals(13, d.totalHead());
        assertEquals(2, d.lines().size());
        assertEquals("Document of identification: sale of livestock", d.title());
        assertEquals("Document of identification: removal of livestock", full("TRANSFER_OUT").title());
    }

    @Test void everyMissingDetailIsWarnedAboutSoItCanBeWrittenInByHand() {
        Document d = AgSaleDocumentRules.build(ID, DAY, "SALE", "Rietvlei", null, null, null, null, new Party(null, null, null),
                new Party(null, null, null), null, null, LINES);
        assertEquals(7, d.warnings().size(), d.warnings().toString());
        assertTrue(d.warnings().get(0).contains("brand mark"));
    }

    @Test void oneMarkIsEnoughToSkipTheMarkWarning() {
        Document d = AgSaleDocumentRules.build(ID, DAY, "SALE", "R", null, null, "Ear", null, new Party("O", null, "A"), new Party("B", "1", "x"), "d", "v", LINES);
        assertTrue(d.warnings().isEmpty(), d.warnings().toString());
        Document blank = AgSaleDocumentRules.build(ID, DAY, "SALE", "R", null, "  ", "", null, new Party("O", null, "A"), new Party("B", "1", "x"), "d", "v", LINES);
        assertEquals(1, blank.warnings().size());
    }

    @Test void descriptionsListWhatIsKnownInOrder() {
        assertEquals("Cattle, female, Bonsmara, born 12 Mar 2022",
                AgSaleDocumentRules.animalDescription("Cattle", "FEMALE", "Bonsmara", LocalDate.of(2022, 3, 12), false));
        assertEquals("Cattle, castrated, born about 1 Jan 2020",
                AgSaleDocumentRules.animalDescription(" Cattle ", "CASTRATED", null, LocalDate.of(2020, 1, 1), true));
        assertEquals("", AgSaleDocumentRules.animalDescription(null, null, " ", null, false));
        assertEquals("Cattle, not known", AgSaleDocumentRules.animalDescription("Cattle", "NOT_KNOWN", null, null, false));
        assertEquals("Sheep, Dorper", AgSaleDocumentRules.groupDescription("Sheep", "Dorper"));
        assertEquals("Sheep", AgSaleDocumentRules.groupDescription("Sheep", null));
    }

    @Test void theLinesAreCopiedSoTheDocumentCannotBeChangedAfterwards() {
        java.util.ArrayList<Line> mutable = new java.util.ArrayList<>(LINES);
        Document d = AgSaleDocumentRules.build(ID, DAY, "SALE", "R", null, "B", null, null, new Party("O", null, "A"), new Party("B", "1", "x"), "d", "v", mutable);
        mutable.clear();
        assertEquals(2, d.lines().size());
        assertThrows(UnsupportedOperationException.class, () -> d.lines().add(new Line("x", "y", 1)));
    }
}
