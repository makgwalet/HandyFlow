package za.co.handyflow.platform.agriculture.application.internal;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** The rules are tested on their own; this checks the sale, status and harvest paths still call them. */
class AgComplianceWiringTest {

    private static final String DIR = "src/main/java/za/co/handyflow/platform/agriculture/";

    private static String src(String name) throws IOException { return Files.readString(Path.of(DIR + name)); }

    @Test void aSaleOrTransferOutChecksWithdrawalBeforeChangingAnything() throws IOException {
        String code = src("application/internal/AgMovementRecordService.java");
        int saleCase = code.indexOf("case \"SALE\", \"TRANSFER_OUT\"");
        int check = code.indexOf("withdrawal.requireAnimalOrGroupClear(");
        int change = code.indexOf("changeStatus(AgLifecycleRules.animalStatusForMovement(type))");
        assertTrue(saleCase > 0 && check > saleCase, "checked inside the sale case");
        assertTrue(change > check, "checked before the animal's status is changed");
        assertTrue(code.contains("req.withdrawalOverrideReason()"));
        assertTrue(code.contains("type, fromArea, toArea, fromFarm, toFarm, count, reason, notes,"), "the override line reaches the saved record");
    }

    @Test void changingAnAnimalToSoldCulledOrTransferredChecksWithdrawalFirst() throws IOException {
        String code = src("application/internal/AgAnimalService.java");
        int check = code.indexOf("withdrawal.requireAnimalOrGroupClear(");
        int change = code.indexOf("animal.changeStatus(req.status())");
        assertTrue(check > 0 && change > check);
        assertTrue(code.contains("AgWithdrawalRules.LEAVES_FOR_MARKET.contains(req.status())"));
        assertTrue(code.contains("animal.addNote("), "the override reason stays on the animal");
    }

    @Test void aHarvestChecksThePreHarvestIntervalAndKeepsTheOverrideReason() throws IOException {
        String code = src("application/internal/AgHarvestRecordService.java");
        assertTrue(code.contains("withdrawal.requireCropClear(tenantId, cropCycleId, req.harvestDate(), req.preHarvestOverrideReason())"));
        assertTrue(code.contains("req.harvestedBy(), harvestedByName, req.laborHours(), notes)"));
    }

    @Test void theIntervalIsStoredWithTheSpray() throws IOException {
        assertTrue(src("application/internal/AgInputApplicationService.java").contains("req.preHarvestIntervalDays()"));
        assertTrue(src("domain/model/AgInputApplication.java").contains("pre_harvest_interval_days"));
    }

    @Test void everyComplianceEndpointIsGuardedAndEditingMarksNeedsManage() throws IOException {
        String c = src("api/AgComplianceController.java");
        assertEquals(4, c.split("@PreAuthorize", -1).length - 1);
        assertEquals(4, c.split("featureGuard.requireModule\\(\"agriculture\"\\)", -1).length - 1);
        int put = c.indexOf("@PutMapping(\"/farms/{farmId}/marks\")");
        assertTrue(c.substring(put, c.indexOf("public", put)).contains("AGRICULTURE_MANAGE"));
    }

    @Test void theWithdrawalQueriesAreLimitedToTheTenantAndTheWindow() throws IOException {
        String r = src("domain/repository/AgHealthEventRepository.java");
        assertTrue(r.contains("findWithdrawalTreatmentsForAnimal"));
        assertEquals(2, r.split("e.eventDate >= :since AND e.eventDate <= :asOf", -1).length - 1, "animal and group queries");
        String i = src("domain/repository/AgInputApplicationRepository.java");
        assertTrue(i.contains("a.applicationDate >= :since AND a.applicationDate <= :asOf"));
        assertTrue(i.contains("a.tenantId = :tenantId AND a.cropCycleId = :cropCycleId\n        AND a.preHarvestIntervalDays"));
    }
}
