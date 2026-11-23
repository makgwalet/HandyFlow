package za.co.handyflow.platform.agriculture.application;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Source-scan checks for the Spring layer of "a birth creates the offspring", which a plain unit test cannot reach:
 * the order of calls in the service, the migration, the endpoints and the daily sweep.
 */
class AgBirthWiringTest {

    private static final Path MAIN = Path.of("src/main/java/za/co/handyflow/platform/agriculture");

    private static String read(String relative) throws IOException { return Files.readString(MAIN.resolve(relative)); }

    /** Both parts are there and the first comes before the second (a missing part must not pass as "before"). */
    private static boolean before(String text, String first, String second) {
        int a = text.indexOf(first), b = text.indexOf(second);
        return a >= 0 && b >= 0 && a < b;
    }

    private static int count(String text, String part) {
        int n = 0, at = 0;
        while ((at = text.indexOf(part, at)) >= 0) { n++; at += part.length(); }
        return n;
    }

    @Test void aBirthCreatesAnimalsForADamAndRaisesTheCountForAGroup() throws IOException {
        String s = read("application/internal/AgBreedingRecordService.java");
        String birth = s.substring(s.indexOf("public BreedingRecordResponse recordBirth"), s.indexOf("private List<UUID> createOffspring"));
        assertTrue(birth.contains("if (live > 0)"), "nothing is created for a stillbirth-only birth");
        assertTrue(before(birth, "record.getAnimalId() != null", "createOffspring(tenantId, record, live, req.offspring())"));
        assertTrue(birth.contains("else addToGroup(tenantId, record, live)"));
        assertTrue(before(birth, "record.recordBirth(", "createOffspring("), "the record's own checks run first");
    }

    @Test void theOffspringAreCheckedNumberedAndSavedTogether() throws IOException {
        String s = read("application/internal/AgBreedingRecordService.java");
        String make = s.substring(s.indexOf("private List<UUID> createOffspring"), s.indexOf("private void addToGroup"));
        assertTrue(make.contains("AgBirthRules.createsIndividualAnimals(species.getTrackingMode())"), "a GROUP species gets no animals");
        assertTrue(before(make, "requireOffspringDetail(live, offspring)", "assignTags("));
        assertTrue(before(make, "assignTags(", "AgAnimal.create("));
        assertTrue(make.contains("\"BORN_ON_FARM\""));
        assertTrue(make.contains("record.getSireId()") && make.contains("dam.getId()"), "sire and dam are linked");
        assertTrue(make.contains("calf.markNewborn(record.getId(), tags.get(i).temporary())"));
        assertTrue(before(make, "calf.markNewborn(", "animalRepository.saveAll(born)"));
        assertTrue(make.contains("existsActiveByFarmAndTagNumber"), "a typed tag already on the farm is refused");
    }

    @Test void aGroupBirthWritesALineOnTheCountLog() throws IOException {
        String s = read("application/internal/AgBreedingRecordService.java");
        String grp = s.substring(s.indexOf("private void addToGroup"), s.indexOf("private AgBreedingRecord findByTenantAndId"));
        assertTrue(grp.contains("requireOnFarm"));
        assertTrue(before(grp, "group.increaseCount(live)", "countLog.record("));
        assertTrue(grp.contains("AgGroupCountLog.INCREASE") && grp.contains("\"BREEDING_RECORD\""));
    }

    @Test void theTagIsReplacedOnlyWhenTemporaryAndTheOldOneKept() throws IOException {
        String s = read("application/internal/AgAnimalTagService.java");
        String replace = s.substring(s.indexOf("public AgAnimal replaceTemporaryTag"), s.indexOf("public List<TagHistoryResponse> history"));
        assertTrue(before(replace, "requireTemporaryTag(", "animal.replaceTag("));
        assertTrue(before(replace, "existsActiveByFarmAndTagNumber", "animal.replaceTag("));
        assertTrue(before(replace, "String oldTag = animal.getTagNumber()", "animal.replaceTag("), "the old tag is read before it is replaced");
        assertTrue(replace.contains("historyRepository.save(AgAnimalTagHistory.create("));
    }

    @Test void theTagEndpointsAreGuarded() throws IOException {
        String s = read("api/AgAnimalTagController.java");
        assertTrue(s.contains("@PatchMapping(\"/animals/{id}/tag\")"));
        assertTrue(s.contains("@GetMapping(\"/animals/{id}/tag-history\")"));
        assertEquals(2, count(s, "featureGuard.requireModule(SOURCE_MODULE)"));
        assertTrue(before(s, "@PatchMapping", "AGRICULTURE_MANAGE") && before(s, "AGRICULTURE_MANAGE", "@GetMapping"));
        assertTrue(before(s, "@GetMapping", "AGRICULTURE_READ"));
    }

    @Test void theDailySweepRemindsAboutTemporaryTags() throws IOException {
        String s = read("application/internal/AgNotificationScheduler.java");
        assertTrue(s.contains("sweepTemporaryTags(today);"));
        String sweep = s.substring(s.indexOf("private void sweepTemporaryTags"), s.indexOf("private boolean notifyTagDue"));
        assertTrue(before(sweep, "AgBirthRules.reminderDue(", "notifyTagDue(animal)"));
        assertTrue(before(sweep, "isNoticeDue(", "notifyTagDue(animal)"), "no daily nagging");
        assertTrue(sweep.contains("animal.markTagReminderNotified(today)"));
    }

    @Test void theMigrationAddsTheColumnsTheTableAndIsRepeatable() throws IOException {
        String s = Files.readString(Path.of("src/main/resources/db/migration/V888__agriculture_births_create_offspring.sql"));
        for (String part : new String[]{"tag_temporary BOOLEAN NOT NULL DEFAULT FALSE", "birth_record_id UUID", "tag_reminder_notified_on DATE",
                "tag_reminder_days INTEGER", "chk_ag_species_tag_reminder_days", "tag_reminder_days BETWEEN 1 AND 365", "CREATE TABLE IF NOT EXISTS ag_animal_tag_history",
                "chk_ag_animal_tag_history_differ"})
            assertTrue(s.contains(part), "missing: " + part);
        assertEquals(4, count(s, "ADD COLUMN IF NOT EXISTS"), "the animal and species columns are added only if missing");
    }

    @Test void oldCallersStillCompile() throws IOException {
        assertTrue(read("dto/AnimalResponse.java").contains("withdrawalOverrideReason, withdrawalHeldUntil, false, null)"), "22- and 24-argument AnimalResponse constructors remain");
        assertTrue(read("dto/RecordBirthRequest.java").contains("this(actualBirthDate, offspringCount, stillbornCount, null)"));
        assertTrue(read("dto/BreedingRecordResponse.java").contains("offspringCount, stillbornCount, notes, createdAt, updatedAt, List.of())"));
        assertTrue(read("dto/CreateSpeciesRequest.java").contains("maturityWeightKg, null)"));
        assertTrue(read("dto/UpdateSpeciesRequest.java").contains("maturityWeightKg, null)"));
        assertTrue(read("dto/SpeciesResponse.java").contains("createdAt, null)"));
    }

    @Test void theConstructorsHandBuiltTestsUseAreUnchanged() throws IOException {
        // AgAnimalServiceBreedingStockTest builds AgAnimalService with six arguments, and AgAnimalControllerTest wires the animal controller:
        // the tag work lives in its own service and controller so neither changes.
        assertEquals(6, count(read("application/internal/AgAnimalService.java"), "    private final "));
        assertFalse(read("api/AgAnimalTagController.java").isBlank());
        assertTrue(read("application/internal/AgAnimalService.java").contains("a.isTagTemporary(), a.getBirthRecordId()"), "the animal response carries the tag flag");
    }
}
