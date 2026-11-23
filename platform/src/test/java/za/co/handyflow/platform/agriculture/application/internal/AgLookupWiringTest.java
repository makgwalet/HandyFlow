package za.co.handyflow.platform.agriculture.application.internal;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** The rules are tested on their own; this checks the services and controllers still use them. */
class AgLookupWiringTest {

    private static final String DIR = "src/main/java/za/co/handyflow/platform/agriculture/";

    private static String src(String name) throws IOException { return Files.readString(Path.of(DIR + name)); }

    @Test void speciesAndCropTypeNamesCannotBeDuplicatedOnCreateOrRename() throws IOException {
        for (String s : new String[] {"AgSpeciesService", "AgCropTypeService"}) {
            String code = src("application/internal/" + s + ".java");
            assertEquals(2, code.split("AgLookupDefaults\\.requireNewName\\(", -1).length - 1, s + ": create and rename");
        }
    }

    @Test void standardListsAreAddedOnlyForWhatIsMissing() throws IOException {
        assertTrue(src("application/internal/AgSpeciesService.java").contains("AgLookupDefaults.missingSpecies("));
        assertTrue(src("application/internal/AgCropTypeService.java").contains("AgLookupDefaults.missingCropTypes("));
    }

    @Test void addingTheStandardListsNeedsManagePermission() throws IOException {
        for (String c : new String[] {"AgSpeciesController", "AgCropTypeController"}) {
            String code = src("api/" + c + ".java");
            int at = code.indexOf("@PostMapping(\"/defaults\")");
            assertTrue(at > 0, c);
            assertTrue(code.substring(at, at + 140).contains("AGRICULTURE_MANAGE"), c);
        }
    }
}
