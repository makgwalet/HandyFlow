package za.co.handyflow.platform.agriculture.application;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** Source-scan checks for the Spring layer of the animal import. Run with the platform folder as the working directory. */
class AgAnimalImportWiringTest {

    private static final Path MAIN = Path.of("src/main/java/za/co/handyflow/platform/agriculture");

    private static String read(String relative) throws IOException { return Files.readString(MAIN.resolve(relative)); }

    private static boolean before(String text, String first, String second) {
        int a = text.indexOf(first), b = text.indexOf(second);
        return a >= 0 && b >= 0 && a < b;
    }

    @Test void theFarmIsCheckedAndTheFileIsPlannedBeforeAnythingIsSaved() throws IOException {
        String s = read("application/internal/AgAnimalImportService.java");
        assertTrue(before(s, "references.requireActiveFarm(tenantId, farmId)", "AgAnimalImportRules.plan("));
        assertTrue(before(s, "AgAnimalImportRules.plan(", "animalRepository.saveAll(created)"));
        assertTrue(s.contains("@Transactional\n    public AnimalImportResponse importAnimals"), "one transaction, so a failure leaves nothing half done");
    }

    @Test void aDryRunCreatesNothing() throws IOException {
        String s = read("application/internal/AgAnimalImportService.java");
        assertTrue(s.contains("if (!req.dryRun()) {"));
        assertTrue(before(s, "if (!req.dryRun()) {", "animalRepository.saveAll(created)"), "saving happens only inside the not-a-dry-run branch");
        assertEquals(1, s.split("saveAll\\(", -1).length - 1, "the only write");
    }

    @Test void onlyRowsThatPassedAreCreated() throws IOException {
        String s = read("application/internal/AgAnimalImportService.java");
        assertTrue(s.contains("if (!AgAnimalImportRules.OK.equals(r.status())) continue;"));
        assertTrue(s.contains("AgAnimal.create(tenantId, farmId, p.areaId(), null, p.speciesId(), p.tagNumber()"));
    }

    @Test void everyTagOnThisFarmIsLoadedForTheDuplicateCheck() throws IOException {
        String s = read("application/internal/AgAnimalImportService.java");
        assertTrue(s.contains("animalRepository.findTagNumbersStartingWith(tenantId, farmId, \"\")"));
        assertTrue(s.contains("tag.toLowerCase(Locale.ROOT)"));
    }

    @Test void speciesAndAreasComeFromThisTenantAndFarmOnly() throws IOException {
        String s = read("application/internal/AgAnimalImportService.java");
        assertTrue(s.contains("speciesRepository.findAllActive(tenantId,"));
        assertTrue(s.contains("areaRepository.findAllActiveForFarm(tenantId, farmId,"));
    }

    @Test void theExistingAnimalServiceIsLeftAlone() throws IOException {
        // its constructor is used by hand-built tests, so the import has its own service
        String s = read("application/internal/AgAnimalService.java");
        assertFalse(s.contains("AgAnimalImport"));
    }

    @Test void theEndpointNeedsManageAndTheModule() throws IOException {
        String c = read("api/AgAnimalImportController.java");
        assertTrue(c.contains("@PostMapping(\"/{farmId}/animals/import\")"));
        assertTrue(c.contains("hasAuthority('AGRICULTURE_MANAGE')"));
        assertTrue(c.contains("featureGuard.requireModule(\"agriculture\")"));
        assertTrue(c.contains("@Valid @RequestBody AnimalImportRequest request"));
        assertTrue(c.contains("TenantContext.getTenantIdAsObject()"));
    }

    @Test void theRequestIsCappedAtFiveHundredRows() throws IOException {
        assertTrue(read("dto/AnimalImportRequest.java").contains("@NotNull @Size(min = 1, max = 500) List<Row> rows"));
    }
}
