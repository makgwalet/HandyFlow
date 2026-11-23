package za.co.handyflow.platform.agriculture.application.internal;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** The rules are tested on their own; this checks the services still call them (a missed call compiles and runs). */
class AgMoveAndFeedWiringTest {

    private static final String DIR = "src/main/java/za/co/handyflow/platform/agriculture/application/internal/";

    private static String src(String name) throws IOException { return Files.readString(Path.of(DIR + name)); }

    @Test void movingAnAnimalOrGroupChecksTheArea() throws IOException {
        assertTrue(src("AgAnimalService.java").contains("AgMovementRules.requireUsableArea("));
        assertTrue(src("AgGroupService.java").contains("AgMovementRules.requireUsableArea("));
    }

    @Test void anAreaTransferRecordMovesTheAnimalAndTakesFromFromTheAnimal() throws IOException {
        String s = src("AgMovementRecordService.java");
        assertTrue(s.contains("AgMovementRules.requireUsableArea("));
        assertTrue(s.contains("target.animal().moveTo(to)"));
        assertTrue(s.contains("target.group().moveTo(to)"));
        assertTrue(s.contains("from = target.currentAreaId();"));
        assertTrue(s.contains("AgMovementRules.requireDifferentArea(from, to)"));
    }

    @Test void feedFromStockChecksConvertsAndDefaultsCost() throws IOException {
        String s = src("AgFeedRecordService.java");
        assertTrue(s.contains("AgFeedRules.requireStockItem("));
        assertTrue(s.contains("AgFeedRules.quantityInItemUnit("));
        assertTrue(s.contains("AgFeedRules.costPerKg("));
        assertTrue(s.contains("item.issue(issued)"), "the converted quantity, not the kilograms, must be issued");
        assertFalse(s.contains("item.issue(req.quantityKg())"), "stock must not be reduced by raw kilograms");
    }
}
