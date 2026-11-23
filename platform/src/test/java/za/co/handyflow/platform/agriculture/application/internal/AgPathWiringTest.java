package za.co.handyflow.platform.agriculture.application.internal;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** The rules are tested on their own; this checks the controllers and schedulers still use them. */
class AgPathWiringTest {

    private static final String DIR = "src/main/java/za/co/handyflow/platform/agriculture/";

    private static String src(String name) throws IOException { return Files.readString(Path.of(DIR + name)); }

    private static int count(String text, String part) { return text.split(java.util.regex.Pattern.quote(part), -1).length - 1; }

    @Test void everyAnimalAndGroupRecordPostChecksThePathId() throws IOException {
        String animals = src("api/AgAnimalController.java");
        String groups = src("api/AgGroupController.java");
        assertEquals(5, count(animals, "AgPathRules.requireAnimal(id, request.animalId(), request.groupId());"), "health, breeding, movement, mortality, feed");
        assertEquals(5, count(groups, "AgPathRules.requireGroup(id, request.animalId(), request.groupId());"), "health, breeding, movement, mortality, feed");
        assertEquals(0, count(animals, "requireGroup("));
        assertEquals(0, count(groups, "requireAnimal("));
    }

    @Test void theCheckRunsBeforeTheServiceIsCalled() throws IOException {
        for (String f : new String[] {"api/AgAnimalController.java", "api/AgGroupController.java"}) {
            String code = src(f);
            int from = 0;
            while ((from = code.indexOf("AgPathRules.require", from)) >= 0) {
                int svc = code.indexOf("Service.create", from);
                int next = code.indexOf("@PostMapping", from);
                assertTrue(svc > from && (next < 0 || svc < next), f + ": the service call follows the check in the same method");
                from += 10;
            }
        }
    }

    @Test void remindersLinkToRealPagesAndNotToInventedOnes() throws IOException {
        String health = src("application/internal/AgNotificationScheduler.java");
        String crops = src("application/internal/AgCropNotificationScheduler.java");
        assertTrue(health.contains("AgActionUrls.animalOrGroup(target.get().farmId(), event.getAnimalId(), event.getGroupId())"));
        assertTrue(health.contains("AgActionUrls.inventory(item.getFarmId())"));
        assertTrue(crops.contains("AgActionUrls.cropCycles(target.get().farmId())"));
        for (String code : new String[] {health, crops}) {
            assertFalse(code.contains("\"/agriculture/"), "no hand-written URLs; they come from AgActionUrls");
        }
    }
}
