package za.co.handyflow.platform.agriculture.application;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** Source-scan checks for the Spring layer of "kg per bag", which a plain unit test cannot reach. Run with the platform folder as the working directory. */
class AgPackWiringTest {

    private static final Path MAIN = Path.of("src/main/java/za/co/handyflow/platform/agriculture");

    private static String read(String relative) throws IOException { return Files.readString(MAIN.resolve(relative)); }

    private static int count(String text, String part) {
        int n = 0, at = 0;
        while ((at = text.indexOf(part, at)) >= 0) { n++; at += part.length(); }
        return n;
    }

    private static boolean before(String text, String first, String second) {
        int a = text.indexOf(first), b = text.indexOf(second);
        return a >= 0 && b >= 0 && a < b;
    }

    @Test void feedResolvesKgOrBagsThenConvertsThroughThePackSize() throws IOException {
        String s = read("application/internal/AgFeedRecordService.java");
        assertTrue(s.contains("AgFeedRules.resolveQuantityKg(req.quantityKg(), req.quantityBags()"));
        assertTrue(before(s, "AgFeedRules.requireStockItem(", "AgFeedRules.resolveQuantityKg("), "the item is loaded and checked first");
        assertTrue(before(s, "AgFeedRules.resolveQuantityKg(", "AgFeedRules.requirePositive(quantityKg)"));
        assertTrue(s.contains("AgFeedRules.quantityInItemUnit(quantityKg, item.getUnitOfMeasure(), item.getItemName(), item.getPackSizeKg())"));
        assertTrue(s.contains("AgFeedRules.costPerKg(req.costPerKg(), item.getUnitCost(), item.getUnitOfMeasure(), item.getPackSizeKg())"));
        assertTrue(s.contains("movement.recordPackSize(item.getPackSizeKg())"), "the factor used is kept on the movement");
        assertFalse(s.contains("req.quantityKg(), item.getUnitOfMeasure()"), "the typed kg must not bypass the resolved quantity");
        assertFalse(s.contains("item.issue(req.quantityKg())"));
        assertTrue(s.contains("req.inventoryItemId(), req.feedType(), quantityKg, costPerKg, req.notes()"), "the record keeps the resolved kilograms");
    }

    @Test void everyStockMovementPostedByTheItemServiceKeepsThePackSize() throws IOException {
        String s = read("application/internal/AgInventoryItemService.java");
        assertEquals(3, count(s, "AgStockMovement.create("), "receipt, issue and adjustment");
        assertEquals(3, count(s, "movement.recordPackSize(item.getPackSizeKg())"));
        assertTrue(s.contains("item.setPackSize(req.packSizeKg())"));
        assertEquals(2, count(s, "item.setPackSize(req.packSizeKg())"), "on create and on update");
        assertTrue(s.contains("AgFeedRules.costPerKg(null, i.getUnitCost(), i.getUnitOfMeasure(), i.getPackSizeKg())"));
    }

    @Test void theMovementListShowsThePackSizeUsed() throws IOException {
        assertTrue(read("application/internal/AgStockMovementService.java").contains("m.getPackSizeKg()"));
    }

    @Test void theEntitiesAndMigrationAgree() throws IOException {
        String item = read("domain/model/AgInventoryItem.java");
        assertTrue(item.contains("@Column(name = \"pack_size_kg\", precision = 10, scale = 3)"));
        assertTrue(item.contains("AgPackRules.requirePackSize(this.unitOfMeasure, packSizeKg)"));
        String mov = read("domain/model/AgStockMovement.java");
        assertTrue(mov.contains("@Column(name = \"pack_size_kg\", precision = 10, scale = 3)"));
        assertTrue(mov.contains("if (this.packSizeKg != null) throw new IllegalStateException(\"the pack size of a posted movement cannot be changed\")"));
        String mig = Files.readString(Path.of("src/main/resources/db/migration/V889__agriculture_stock_pack_size.sql"));
        assertTrue(mig.contains("ALTER TABLE ag_inventory_items ADD COLUMN IF NOT EXISTS pack_size_kg NUMERIC(10,3)"));
        assertTrue(mig.contains("ALTER TABLE ag_stock_movements ADD COLUMN IF NOT EXISTS pack_size_kg NUMERIC(10,3)"));
        assertTrue(mig.contains("pack_size_kg > 0 AND pack_size_kg <= 10000"));
    }

    @Test void oldConstructorsAreKept() throws IOException {
        assertTrue(read("dto/CreateFeedRecordRequest.java").contains("BigDecimal quantityKg, BigDecimal costPerKg, String notes) {"));
        assertFalse(read("dto/CreateFeedRecordRequest.java").contains("@NotNull BigDecimal quantityKg"), "bags can stand in for kg");
        assertTrue(read("dto/CreateInventoryItemRequest.java").contains("BigDecimal reorderLevel, BigDecimal unitCost, String supplier) {"));
        assertTrue(read("dto/UpdateInventoryItemRequest.java").contains("String supplier, String notes) {"));
        assertTrue(read("dto/InventoryItemResponse.java").contains("UUID supplierId, String supplierName) {"));
        assertTrue(read("dto/StockMovementResponse.java").contains("UUID supplierId, String supplierName) {"));
    }
}
