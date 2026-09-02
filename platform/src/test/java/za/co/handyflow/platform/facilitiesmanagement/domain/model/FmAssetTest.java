package za.co.handyflow.platform.facilitiesmanagement.domain.model;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.shared.TenantId;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class FmAssetTest {

    private FmAsset newAsset() {
        return FmAsset.create(TenantId.generate(), UUID.randomUUID(), "HVAC-01", "Rooftop AHU",
                "HVAC", "Roof - East Wing", "Daikin", "VRV-IV", "SN12345", null, null, "HIGH", null);
    }

    @Test
    void newAssetStartsOperational() {
        FmAsset asset = newAsset();
        assertEquals("OPERATIONAL", asset.getStatus());
    }

    @Test
    void statusTransitionsWorkWhileNotDecommissioned() {
        FmAsset asset = newAsset();
        asset.markDown();
        assertEquals("DOWN", asset.getStatus());
        asset.sendToMaintenance();
        assertEquals("MAINTENANCE", asset.getStatus());
        asset.markOperational();
        assertEquals("OPERATIONAL", asset.getStatus());
    }

    @Test
    void decommissionIsTerminal() {
        FmAsset asset = newAsset();
        asset.decommission();
        assertEquals("DECOMMISSIONED", asset.getStatus());
        assertThrows(IllegalStateException.class, asset::markOperational);
        assertThrows(IllegalStateException.class, asset::decommission);
    }

    @Test
    void softDeleteMarksDeleted() {
        FmAsset asset = newAsset();
        assertFalse(asset.isDeleted());
        asset.softDelete();
        assertTrue(asset.isDeleted());
    }
}
