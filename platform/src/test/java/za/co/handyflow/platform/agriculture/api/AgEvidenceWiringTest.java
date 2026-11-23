package za.co.handyflow.platform.agriculture.api;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Reads the controller sources, because a wrong call here (the unscoped download or detach, or no parent check on attach)
 * compiles and runs and only shows up as one module reading another module's files.
 */
class AgEvidenceWiringTest {

    private static final String DIR = "src/main/java/za/co/handyflow/platform/agriculture/api/";

    private static String src(String name) throws IOException { return Files.readString(Path.of(DIR + name)); }

    @Test void everyAgricultureControllerUsesTheScopedEvidenceCalls() throws IOException {
        for (String c : new String[]{"AgAnimalController.java", "AgGroupController.java", "AgCropCycleController.java"}) {
            String s = src(c);
            assertTrue(s.contains("evidenceFacade.downloadScoped("), c + " must download through downloadScoped");
            assertTrue(s.contains("evidenceFacade.detachScoped("), c + " must detach through detachScoped");
            assertFalse(s.contains("evidenceFacade.download("), c + " still calls the unscoped download");
            assertFalse(s.contains("evidenceFacade.detach("), c + " still calls the unscoped detach");
        }
    }

    @Test void attachChecksTheParentExists() throws IOException {
        assertTrue(src("AgAnimalController.java").contains("animalService.requireExists(tenantId, id);"));
        assertTrue(src("AgGroupController.java").contains("groupService.requireExists(tenantId, id);"));
        assertTrue(src("AgCropCycleController.java").contains("cropCycleService.requireExists(tenantId, id);"));
    }

    @Test void downloadHeaderIsBuiltByTheHelper() throws IOException {
        for (String c : new String[]{"AgAnimalController.java", "AgGroupController.java", "AgCropCycleController.java"})
            assertFalse(src(c).contains("\"attachment; filename=\\\"\""), c + " builds the header by hand");
    }
}
