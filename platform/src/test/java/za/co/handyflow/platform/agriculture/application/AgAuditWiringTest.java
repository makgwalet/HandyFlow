package za.co.handyflow.platform.agriculture.application;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Source-scan checks for the added-by / changed-by stamp. Run with the platform folder as the working directory. */
class AgAuditWiringTest {

    private static final Path MAIN = Path.of("src/main/java/za/co/handyflow/platform/agriculture");
    private static final Path MIGRATIONS = Path.of("src/main/resources/db/migration");
    private static final List<String> ENTITIES = List.of("AgAnimal", "AgGroup", "AgHealthEvent", "AgBreedingRecord", "AgInventoryItem", "AgCropCycle");
    private static final List<String> LOG_ENTITIES = List.of("AgWeightRecord", "AgMortalityRecord", "AgMovementRecord", "AgFeedRecord", "AgScoutingRecord", "AgHarvestRecord", "AgInputApplication");
    private static final List<String> LOG_TABLES = List.of("ag_weight_records", "ag_mortality_records", "ag_movement_records", "ag_feed_records", "ag_scouting_records", "ag_harvest_records", "ag_input_applications");
    private static final List<String> TABLES = List.of("ag_animals", "ag_groups", "ag_health_events", "ag_breeding_records", "ag_inventory_items", "ag_crop_cycles");

    private static String read(String relative) throws IOException { return Files.readString(MAIN.resolve(relative)); }

    private static boolean before(String text, String first, String second) {
        int a = text.indexOf(first), b = text.indexOf(second);
        return a >= 0 && b >= 0 && a < b;
    }

    @Test void everyStampedRecordUsesTheListenerAndKeepsAStamp() throws IOException {
        for (String e : ENTITIES) {
            String s = read("domain/model/" + e + ".java");
            assertTrue(s.contains("@EntityListeners(AgAuditListener.class)"), e + " needs the listener");
            assertTrue(s.contains("implements AgAudited"), e + " must be AgAudited");
            assertTrue(s.contains("@Embedded\n    private AgAuditStamp audit = new AgAuditStamp();"), e + " needs the stamp");
        }
    }

    @Test void theEarlierMigrationStillCoversTheFirstSixTables() throws IOException {
        String sql = Files.readString(MIGRATIONS.resolve("V890__agriculture_audit_columns.sql"));
        for (String t : TABLES) assertTrue(sql.contains("ALTER TABLE " + t + " "), t);
    }

    @Test void everyLogRecordUsesTheListenerAndKeepsAStamp() throws IOException {
        for (String e : LOG_ENTITIES) {
            String s = read("domain/model/" + e + ".java");
            assertTrue(s.contains("@EntityListeners(AgAuditListener.class)"), e + " needs the listener");
            assertTrue(s.contains("implements AgAudited"), e + " must be AgAudited");
            assertTrue(s.contains("@Embedded\n    private AgAuditStamp audit = new AgAuditStamp();"), e + " needs the stamp");
        }
    }

    @Test void theMigrationAddsTheFourColumnsToEveryStampedTable() throws IOException {
        String sql = Files.readString(MIGRATIONS.resolve("V891__agriculture_audit_columns_logs.sql"));
        for (String t : LOG_TABLES) {
            String line = sql.lines().filter(l -> l.startsWith("ALTER TABLE " + t + " ")).findFirst().orElse("");
            for (String c : List.of("created_by UUID", "created_by_name VARCHAR(255)", "updated_by UUID", "updated_by_name VARCHAR(255)"))
                assertTrue(line.contains("ADD COLUMN IF NOT EXISTS " + c), t + " needs " + c);
        }
    }

    @Test void aRecordThatCannotBeEditedReportsNoLastChange() throws IOException {
        String s = read("application/internal/AgAuditService.java");
        assertTrue(s.contains("boolean editable = record.getUpdatedAt() != null;"));
        assertTrue(s.contains("editable && s != null ? s.getUpdatedByName() : null"));
        assertTrue(read("domain/model/AgAudited.java").contains("default Instant getUpdatedAt() { return null; }"));
    }

    @Test void theBatchOnlyReturnsTheBusinessesOwnRecordsAndChecksTheInput() throws IOException {
        String s = read("application/internal/AgAuditService.java");
        assertTrue(before(s, "AgAuditRules.requireType(type)", "AgAuditRules.requireIds(ids)"));
        assertTrue(before(s, "AgAuditRules.requireIds(ids)", "em.createQuery("));
        assertTrue(s.contains("WHERE e.id IN :ids"));
        assertTrue(s.contains("if (record.getTenantId().equals(tenantId)) out.put(record.getId(), toResponse(record));"));
        String c = read("api/AgAuditController.java");
        assertTrue(c.contains("@GetMapping(\"/{type}\")"));
        String batch = c.substring(c.indexOf("@GetMapping(\"/{type}\")"));
        assertTrue(batch.contains("hasAuthority('AGRICULTURE_READ')"));
        assertTrue(before(batch, "featureGuard.requireModule(\"agriculture\")", "auditService.getMany("));
    }

    @Test void theStampColumnsMatchTheMigration() throws IOException {
        String s = read("domain/model/AgAuditStamp.java");
        for (String c : List.of("created_by", "created_by_name", "updated_by", "updated_by_name")) assertTrue(s.contains("name = \"" + c + "\""), c);
    }

    @Test void aChangeNeverOverwritesWhoAddedTheRecord() throws IOException {
        String s = read("domain/model/AgAuditListener.java");
        assertTrue(before(s, "public void onUpdate", "recordUpdated(w.id(), w.name())"));
        assertFalse(before(s, "public void onUpdate", "recordCreated"), "only creation records the creator");
        String stamp = read("domain/model/AgAuditStamp.java");
        assertTrue(stamp.contains("public void recordUpdated"));
    }

    @Test void withNobodySignedInAChangeLeavesTheStampAlone() throws IOException {
        String s = read("domain/model/AgAuditListener.java");
        assertTrue(before(s, "if (w.id() == null && w.name() == null) return;", "recordUpdated(w.id(), w.name())"));
    }

    @Test void anOldRecordWithNoStampGetsOneOnFirstChange() throws IOException {
        String s = read("domain/model/AgAuditListener.java");
        assertTrue(s.contains("if (a.getAudit() == null) a.setAudit(new AgAuditStamp());"));
    }

    @Test void theNameGoesThroughTheCleaningRule() throws IOException {
        assertTrue(read("domain/model/AgAuditListener.java").contains("AgAuditRules.cleanName(id, name)"));
    }

    @Test void theServiceHidesOtherBusinessesRecordsAndChecksTheType() throws IOException {
        String s = read("application/internal/AgAuditService.java");
        assertTrue(s.contains("em.find(ENTITIES.get(AgAuditRules.requireType(type)), id)"), "the type is checked before anything is read");
        assertTrue(s.contains("record == null || !record.getTenantId().equals(tenantId)"));
        assertTrue(s.contains("@Transactional(readOnly = true)"));
        for (String e : ENTITIES) assertTrue(s.contains(e + ".class"), e + " must be reachable");
        for (String e : LOG_ENTITIES) assertTrue(s.contains(e + ".class"), e + " must be reachable");
    }

    @Test void theEndpointNeedsReadAndTheModule() throws IOException {
        String c = read("api/AgAuditController.java");
        assertTrue(c.contains("hasAuthority('AGRICULTURE_READ')"));
        assertTrue(before(c, "featureGuard.requireModule(\"agriculture\")", "auditService.get("));
        assertTrue(c.contains("@GetMapping(\"/{type}/{id}\")"));
    }
}
