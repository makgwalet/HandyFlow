package za.co.handyflow.platform.earthmoving;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.earthmoving.domain.model.*;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class EarthmovingEntityTest {

    private static EarthAsset asset() {
        return EarthAsset.create(TenantId.generate(), "CAT D9", "D9-001", "DOZER", "Caterpillar", "D9T", 2020,
                "SER1", "GP1", "OWN", null, null, null, new BigDecimal("18500"), new BigDecimal("2200"), "n");
    }

    @Test
    void editChangesDetailsAndLeavesStatusHoursAndSiteAlone() {
        EarthAsset a = asset();
        a.updateHours(new BigDecimal("120.5"));
        a.deployTo("Mine A", "Anglo");
        a.updateDetails("CAT D9T", " D9-002 ", "dozer", "Cat", "D9T2", 2021, "SER2", "GP2", "OWN", null, null, null,
                new BigDecimal("19000"), new BigDecimal("2300"), new BigDecimal("500"), "edited");
        assertEquals("CAT D9T", a.getName());
        assertEquals("D9-002", a.getFleetNumber());
        assertEquals("DOZER", a.getAssetType());
        assertEquals("SER2", a.getSerialNumber());
        assertEquals(new BigDecimal("500"), a.getServiceIntervalHours());
        assertEquals(AssetStatus.DEPLOYED, a.getStatus());
        assertEquals(new BigDecimal("120.5"), a.getCurrentHours());
        assertEquals("Mine A", a.getCurrentSite());
    }

    @Test
    void editWithBlankFleetNumberClearsIt() {
        EarthAsset a = asset();
        a.updateDetails("X", "  ", "DOZER", null, null, null, null, null, "OWN", null, null, null, null, null, null, null);
        assertNull(a.getFleetNumber());
        assertEquals(new BigDecimal("250"), a.getServiceIntervalHours()); // null interval keeps the old one
    }

    @Test
    void hoursOnlyCountUp() {
        EarthAsset a = asset();
        a.updateHours(new BigDecimal("100"));
        assertThrows(IllegalArgumentException.class, () -> a.updateHours(new BigDecimal("99.9")));
    }

    @Test
    void leavingMaintenanceToAvailableIsAllowedByTheStateMachineButTheServiceGatesIt() {
        EarthAsset a = asset();
        a.sendToMaintenance();
        a.returnToYard();
        assertEquals(AssetStatus.AVAILABLE, a.getStatus());
        a.retire();
        assertThrows(InvalidAssetStatusTransitionException.class, a::returnToYard);
    }

    @Test
    void aBrokenDownMachineCanBeReleasedAfterRepairButNotDeployedDirectly() {
        EarthAsset a = asset();
        a.deployTo("Mine A", "Anglo");
        a.breakdown();
        assertThrows(InvalidAssetStatusTransitionException.class, () -> a.deployTo("Mine B", "X"));
        a.returnToYard();
        assertEquals(AssetStatus.AVAILABLE, a.getStatus());
    }

    @Test
    void shiftCompletionChecksTimesAndReadings() {
        Instant start = Instant.parse("2026-10-15T06:00:00Z");
        OperatorLog log = OperatorLog.create(TenantId.generate(), UUID.randomUUID(), null, "Sipho", "Mine A",
                start, new BigDecimal("100"));
        assertThrows(IllegalArgumentException.class, () -> log.complete(start.minusSeconds(1), new BigDecimal("101"), null, null));
        assertThrows(IllegalArgumentException.class, () -> log.complete(start.plusSeconds(60), new BigDecimal("99"), null, null));
        assertNull(log.getEndedAt()); // a rejected completion leaves the shift open
        log.complete(start.plusSeconds(36000), new BigDecimal("108.5"), new BigDecimal("140"), "ok");
        assertEquals(0, new BigDecimal("8.5").compareTo(log.getHoursLogged()));
    }

    @Test
    void statusEventKeepsWhoWhyAndTheMeterReading() {
        UUID actor = UUID.randomUUID();
        EarthStatusEvent e = EarthStatusEvent.of(TenantId.generate(), UUID.randomUUID(), AssetStatus.BREAKDOWN,
                AssetStatus.AVAILABLE, "Pump replaced and tested", actor, new BigDecimal("812.0"));
        assertEquals("BREAKDOWN", e.getFromStatus());
        assertEquals("AVAILABLE", e.getToStatus());
        assertEquals(actor, e.getActorUserId());
        assertEquals("Pump replaced and tested", e.getReason());
        assertNotNull(e.getHappenedAt());
    }
}
