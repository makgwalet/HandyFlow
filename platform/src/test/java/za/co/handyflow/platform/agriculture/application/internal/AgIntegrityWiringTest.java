package za.co.handyflow.platform.agriculture.application.internal;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** The rules are tested on their own; this checks the services and the item still call them (a missed call compiles and runs). */
class AgIntegrityWiringTest {

    private static final String DIR = "src/main/java/za/co/handyflow/platform/agriculture/";

    private static String src(String name) throws IOException { return Files.readString(Path.of(DIR + name)); }

    @Test void newAnimalsAndGroupsAreCheckedAgainstTheirReferences() throws IOException {
        for (String s : new String[] {"AgAnimalService", "AgGroupService"}) {
            String code = src("application/internal/" + s + ".java");
            assertTrue(code.contains("references.requireActiveFarm("), s);
            assertTrue(code.contains("references.requireSpecies("), s);
            assertTrue(code.contains("references.checkPlacement("), s);
        }
        assertTrue(src("application/internal/AgAnimalService.java").contains("references.checkParents("));
    }

    @Test void editsAreCheckedToo() throws IOException {
        String animal = src("application/internal/AgAnimalService.java");
        assertEquals(2, animal.split("references\\.checkPlacement\\(", -1).length - 1, "create and update");
        assertEquals(2, animal.split("references\\.checkParents\\(", -1).length - 1, "create and update");
        assertEquals(2, src("application/internal/AgGroupService.java").split("references\\.checkPlacement\\(", -1).length - 1);
    }

    @Test void thingsAddedToAFarmNeedAnActiveFarm() throws IOException {
        for (String s : new String[] {"AgProductionAreaService", "AgEnterpriseService", "AgInventoryItemService"})
            assertTrue(src("application/internal/" + s + ".java").contains("references.requireActiveFarm("), s);
    }

    @Test void aFarmWithLiveHoldingsCannotBeDeactivatedOrDeleted() throws IOException {
        String farm = src("application/internal/AgFarmService.java");
        assertTrue(farm.contains("requireNothingLive(tenantId, farm, \"deactivated\")"));
        assertTrue(farm.contains("requireNothingLive(tenantId, farm, \"deleted\")"));
        assertTrue(farm.contains("AgReferenceRules.requireNoHoldings("));
        assertTrue(farm.contains("countWithStockForFarm"));
    }

    @Test void stockAdjustmentsKeepTheirDirectionAndReason() throws IOException {
        String inv = src("application/internal/AgInventoryItemService.java");
        assertTrue(inv.contains("AgStockRules.requireReason(req.notes())"));
        assertTrue(inv.contains("AgStockRules.adjustmentType("));
        assertFalse(inv.contains("delta.abs()"), "the direction must not be thrown away");
    }

    @Test void stockCostAndStatusAreEnforcedOnTheItem() throws IOException {
        String item = src("domain/model/AgInventoryItem.java");
        assertTrue(item.contains("AgStockRules.weightedAverageCost("));
        assertEquals(2, item.split("AgStockRules\\.requireUsable\\(", -1).length - 1, "receive and issue");
    }
}
