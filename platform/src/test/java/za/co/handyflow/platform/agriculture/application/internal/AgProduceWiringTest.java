package za.co.handyflow.platform.agriculture.application.internal;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** The rules are tested on their own; this checks the entity, service, repository and endpoints still use them. */
class AgProduceWiringTest {

    private static final String DIR = "src/main/java/za/co/handyflow/platform/agriculture/";

    private static String src(String name) throws IOException { return Files.readString(Path.of(DIR + name)); }

    private static int count(String text, String part) { return text.split(java.util.regex.Pattern.quote(part), -1).length - 1; }

    @Test void theEventChecksBothPeriodsOnCreateAndOnUpdate() throws IOException {
        String e = src("domain/model/AgHealthEvent.java");
        assertEquals(2, count(e, "AgWithdrawalRules.requireValidDays(milkWithdrawalDays, \"Milk withdrawal period\");"), "create and update");
        assertEquals(2, count(e, "AgWithdrawalRules.requireValidDays(eggWithdrawalDays, \"Egg withdrawal period\");"), "create and update");
        assertTrue(e.contains("@Column(name = \"milk_withdrawal_days\")"));
        assertTrue(e.contains("@Column(name = \"egg_withdrawal_days\")"));
    }

    @Test void theServiceSavesAndReturnsThePeriods() throws IOException {
        String s = src("application/internal/AgHealthEventService.java");
        assertEquals(2, count(s, "req.milkWithdrawalDays(), req.eggWithdrawalDays()"), "create and update");
        assertTrue(s.contains("e.getMilkWithdrawalDays(), e.getEggWithdrawalDays()"));
    }

    @Test void theHoldLookupUsesTheMatchingPeriodAndIgnoresScheduledEvents() throws IOException {
        String w = src("application/internal/AgWithdrawalService.java");
        assertTrue(w.contains("AgWithdrawalRules.produceDays(produce, e.getMilkWithdrawalDays(), e.getEggWithdrawalDays())"));
        assertTrue(w.contains("\"SCHEDULED\".equals(e.getStatus())"));
        assertTrue(w.contains("findProduceTreatmentsForAnimal") && w.contains("findProduceTreatmentsForGroup"));
    }

    @Test void theNewEndpointsNeedReadAccessAndTheModule() throws IOException {
        String c = src("api/AgComplianceController.java");
        for (String path : new String[] {"\"/animals/{animalId}/produce-withdrawal\"", "\"/groups/{groupId}/produce-withdrawal\""}) {
            int at = c.indexOf(path);
            assertTrue(at > 0, path);
            String method = c.substring(at, c.indexOf("}\n", at));
            assertTrue(method.contains("hasAuthority('AGRICULTURE_READ')"), path + " needs read access");
            assertTrue(method.contains("featureGuard.requireModule(\"agriculture\")"), path + " checks the module");
        }
    }

    @Test void milkAndEggPeriodsDoNotBlockASaleOfAnimals() throws IOException {
        // meat sales are held by the meat period only; there is no milk or egg sale in the module to hold
        String w = src("application/internal/AgWithdrawalService.java");
        int start = w.indexOf("public String requireAnimalOrGroupClear");
        String method = w.substring(start, w.indexOf("/** Before a harvest", start));
        assertFalse(method.contains("Produce"), "an animal sale checks the meat period only");
    }
}
