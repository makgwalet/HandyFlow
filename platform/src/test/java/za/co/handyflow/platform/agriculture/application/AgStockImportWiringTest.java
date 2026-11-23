package za.co.handyflow.platform.agriculture.application;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** Source-scan checks for the Spring layer of the stock import. Run with the platform folder as the working directory. */
class AgStockImportWiringTest {

    private static final Path MAIN = Path.of("src/main/java/za/co/handyflow/platform/agriculture");

    private static String read(String relative) throws IOException { return Files.readString(MAIN.resolve(relative)); }

    private static boolean before(String text, String first, String second) {
        int a = text.indexOf(first), b = text.indexOf(second);
        return a >= 0 && b >= 0 && a < b;
    }

    @Test void theFarmIsCheckedAndTheFileIsPlannedBeforeAnythingIsSaved() throws IOException {
        String s = read("application/internal/AgStockImportService.java");
        assertTrue(before(s, "references.requireActiveFarm(tenantId, farmId)", "AgStockImportRules.plan("));
        assertTrue(before(s, "AgStockImportRules.plan(", "inventoryItemRepository.saveAll(items)"));
        assertTrue(s.contains("@Transactional\n    public StockImportResponse importItems"), "one transaction, so a failure leaves nothing half done");
    }

    @Test void aDryRunCreatesNothing() throws IOException {
        String s = read("application/internal/AgStockImportService.java");
        assertTrue(before(s, "if (!req.dryRun()) {", "inventoryItemRepository.saveAll(items)"));
        assertTrue(before(s, "if (!req.dryRun()) {", "stockMovementRepository.saveAll(openings)"));
    }

    @Test void onlyRowsThatPassedAreCreated() throws IOException {
        String s = read("application/internal/AgStockImportService.java");
        assertTrue(before(s, "if (!AgStockImportRules.OK.equals(r.status())) continue;", "AgInventoryItem.create("));
    }

    @Test void existingNamesAreComparedInLowerCase() throws IOException {
        String s = read("application/internal/AgStockImportService.java");
        assertTrue(s.contains("getItemName().trim().toLowerCase(Locale.ROOT)"));
        assertTrue(before(s, "findAllActiveForFarm(", "AgStockImportRules.plan("));
    }

    @Test void anOpeningQuantityIsReceivedAndPostedToTheLedgerWithItsPackSize() throws IOException {
        String s = read("application/internal/AgStockImportService.java");
        assertTrue(before(s, "if (p.openingQuantity().signum() > 0) {", "item.receive(p.openingQuantity(), null)"));
        assertTrue(before(s, "item.receive(p.openingQuantity(), null)", "AgStockMovement.create("));
        assertTrue(s.contains("\"RECEIPT\""));
        assertTrue(before(s, "AgStockMovement.create(", "opening.recordPackSize(item.getPackSizeKg())"));
        assertTrue(before(s, "AgInventoryItem.create(", "item.setPackSize(p.packSizeKg())"));
    }

    @Test void theEndpointNeedsManageAndTheModule() throws IOException {
        String c = read("api/AgStockImportController.java");
        assertTrue(c.contains("hasAuthority('AGRICULTURE_MANAGE')"));
        assertTrue(before(c, "featureGuard.requireModule(\"agriculture\")", "importService.importItems("));
        assertTrue(c.contains("/{farmId}/inventory-items/import"));
        assertTrue(c.contains("@Valid @RequestBody"));
    }

    @Test void theRequestIsCappedAtFiveHundredRows() throws IOException {
        String q = read("dto/StockImportRequest.java");
        assertTrue(q.contains("@NotNull @Size(min = 1, max = 500)"));
    }
}
