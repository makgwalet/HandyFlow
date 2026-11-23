package za.co.handyflow.platform.agriculture.application;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** Source-scan checks for the farm suggestions. Run with the platform folder as the working directory. */
class AgSuggestionWiringTest {

    private static final Path MAIN = Path.of("src/main/java/za/co/handyflow/platform/agriculture");

    private static String read(String relative) throws IOException { return Files.readString(MAIN.resolve(relative)); }

    private static boolean before(String text, String first, String second) {
        int a = text.indexOf(first), b = text.indexOf(second);
        return a >= 0 && b >= 0 && a < b;
    }

    @Test void theFarmIsCheckedBeforeAnythingIsRead() throws IOException {
        String s = read("application/internal/AgSuggestionService.java");
        assertTrue(before(s, "references.requireActiveFarm(tenantId, farmId)", "animalRepository.findDistinctBreedsForFarm"));
        assertTrue(s.contains("@Transactional(readOnly = true)"));
    }

    @Test void breedsComeFromAnimalsAndGroupsAndFeedsFromRecordsAndStock() throws IOException {
        String s = read("application/internal/AgSuggestionService.java");
        assertTrue(s.contains("animalRepository.findDistinctBreedsForFarm(tenantId, farmId)"));
        assertTrue(s.contains("groupRepository.findDistinctBreedsForFarm(tenantId, farmId)"));
        assertTrue(s.contains("feedRecordRepository.findDistinctFeedTypesForFarm(tenantId, farmId)"));
        assertTrue(s.contains("inventoryItemRepository.findActiveFeedNamesForFarm(tenantId, farmId)"));
        assertTrue(s.contains("AgSuggestionRules.breedsBySpecies(breeds)") && s.contains("AgSuggestionRules.tidy(feeds)"));
    }

    @Test void everyQueryIsScopedToTheBusinessAndTheFarmAndSkipsDeletedRecords() throws IOException {
        String animals = read("domain/repository/AgAnimalRepository.java");
        String groups = read("domain/repository/AgGroupRepository.java");
        String feeds = read("domain/repository/AgFeedRecordRepository.java");
        String stock = read("domain/repository/AgInventoryItemRepository.java");
        assertTrue(animals.contains("FROM AgAnimal a WHERE a.tenantId = :tenantId AND a.farmId = :farmId AND a.deletedAt IS NULL AND a.breed IS NOT NULL"));
        assertTrue(groups.contains("FROM AgGroup g WHERE g.tenantId = :tenantId AND g.farmId = :farmId AND g.deletedAt IS NULL AND g.breed IS NOT NULL"));
        assertTrue(feeds.contains("f.animalId IN (SELECT a.id FROM AgAnimal a WHERE a.tenantId = :tenantId AND a.farmId = :farmId)"));
        assertTrue(feeds.contains("OR f.groupId IN (SELECT g.id FROM AgGroup g WHERE g.tenantId = :tenantId AND g.farmId = :farmId)"));
        assertTrue(feeds.contains("FROM AgFeedRecord f WHERE f.tenantId = :tenantId AND ("));
        assertTrue(stock.contains("i.farmId = :farmId AND i.deletedAt IS NULL AND i.category = 'FEED' AND i.status = 'ACTIVE'"));
    }

    @Test void theEndpointNeedsReadAndTheModule() throws IOException {
        String c = read("api/AgSuggestionController.java");
        assertTrue(c.contains("hasAuthority('AGRICULTURE_READ')"));
        assertTrue(before(c, "featureGuard.requireModule(\"agriculture\")", "suggestionService.forFarm("));
        assertTrue(c.contains("/{farmId}/suggestions"));
    }
}
