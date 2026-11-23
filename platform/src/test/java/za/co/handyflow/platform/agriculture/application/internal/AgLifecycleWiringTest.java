package za.co.handyflow.platform.agriculture.application.internal;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** The rules are tested on their own; this checks the services still call them and write the count log (a missed call compiles and runs). */
class AgLifecycleWiringTest {

    private static final String DIR = "src/main/java/za/co/handyflow/platform/agriculture/";

    private static String src(String name) throws IOException { return Files.readString(Path.of(DIR + name)); }

    @Test void everyWayTheCountChangesIsLogged() throws IOException {
        String group = src("application/internal/AgGroupService.java");
        assertTrue(group.contains("AgGroupCountLog.DECREASE") && group.contains("AgGroupCountLog.INCREASE"));
        assertTrue(group.contains("AgLifecycleRules.requireReason(req.reason())"));
        assertTrue(src("application/internal/AgMortalityRecordService.java").contains("AgGroupCountLog.MORTALITY"));
        String move = src("application/internal/AgMovementRecordService.java");
        assertTrue(move.contains("AgGroupCountLog.SALE") && move.contains("AgGroupCountLog.TRANSFER_OUT"));
        assertTrue(move.contains("countLog.record("));
    }

    @Test void movementsChangeTheAnimalAndCheckItIsStillOnTheFarm() throws IOException {
        String move = src("application/internal/AgMovementRecordService.java");
        assertTrue(move.contains("AgLifecycleRules.requireOnFarm(target.status()"));
        assertTrue(move.contains("changeStatus(AgLifecycleRules.animalStatusForMovement(type))"));
        assertTrue(move.contains("moveToFarm(toFarm, toArea)"));
        assertTrue(move.contains("AgLifecycleRules.requireWholeGroupForFarmTransfer("));
        assertTrue(move.contains("AgLifecycleRules.requireReason(reason)"), "a group sale or transfer out must give a reason");
    }

    @Test void deathChecksTheGroupHasEnough() throws IOException {
        String m = src("application/internal/AgMortalityRecordService.java");
        assertTrue(m.contains("AgLifecycleRules.requireMortalityCount("));
        assertTrue(m.contains("animal.changeStatus(\"DECEASED\")"));
    }

    @Test void animalStatusGoesThroughTheRules() throws IOException {
        assertTrue(src("domain/model/AgAnimal.java").contains("AgLifecycleRules.requireAnimalStatusChange(this.status, status)"));
    }
}
