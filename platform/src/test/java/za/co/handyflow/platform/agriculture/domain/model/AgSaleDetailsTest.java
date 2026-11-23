package za.co.handyflow.platform.agriculture.domain.model;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AgSaleDetailsTest {

    private static final LocalDate DAY = LocalDate.of(2026, 11, 23);

    private static AgMovementRecord sale(String buyer, String id, String address, String destination, String vehicle) {
        return AgMovementRecord.create(TenantId.generate(), UUID.randomUUID(), null, DAY, "SALE", null, null, UUID.randomUUID(), null, null, "buyer",
                null, buyer, id, address, destination, vehicle);
    }

    @Test void buyerDetailsAreKeptTrimmedAndTheVehicleIsUpperCase() {
        AgMovementRecord m = sale("  P Buyer ", "8001015009087", " 1 Main St ", "Abattoir", "ca 123-456");
        assertEquals("P Buyer", m.getBuyerName());
        assertEquals("8001015009087", m.getBuyerIdNumber());
        assertEquals("1 Main St", m.getBuyerAddress());
        assertEquals("Abattoir", m.getDestination());
        assertEquals("CA 123-456", m.getVehicleRegistration());
    }

    @Test void blankDetailsAreNoDetails() {
        AgMovementRecord m = sale(" ", "", null, "  ", "");
        assertNull(m.getBuyerName());
        assertNull(m.getBuyerIdNumber());
        assertNull(m.getDestination());
        assertNull(m.getVehicleRegistration());
    }

    @Test void aLineBreakOrTooLongDetailIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> sale("a\nb", null, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> sale(null, "9".repeat(61), null, null, null));
        assertThrows(IllegalArgumentException.class, () -> sale(null, null, null, null, "x".repeat(31)));
    }

    @Test void theOldFactoryStillWorksWithoutDetails() {
        AgMovementRecord m = AgMovementRecord.create(TenantId.generate(), UUID.randomUUID(), null, DAY, "AREA_TRANSFER", null, UUID.randomUUID(), null, null, null, null, null);
        assertNull(m.getBuyerName());
        assertNull(m.getVehicleRegistration());
    }

    @Test void farmOwnerDetailsAreSetAndCleared() {
        AgFarm f = AgFarm.create(TenantId.generate(), "Rietvlei", "LIVESTOCK", null, null, null, null);
        f.setOwnerDetails(" J Farmer ", "Plot 4, Bethlehem");
        assertEquals("J Farmer", f.getOwnerName());
        assertEquals("Plot 4, Bethlehem", f.getPhysicalAddress());
        f.setOwnerDetails("J Farmer", " ");
        assertNull(f.getPhysicalAddress());
        assertThrows(IllegalArgumentException.class, () -> f.setOwnerDetails("a".repeat(201), null));
        assertEquals("J Farmer", f.getOwnerName(), "a refused value changes nothing");
    }

    @Test void marksAndOwnerDetailsAreIndependent() {
        AgFarm f = AgFarm.create(TenantId.generate(), "Rietvlei", "LIVESTOCK", null, null, null, null);
        f.setMarks("ZK 1", null, null);
        f.setOwnerDetails("J Farmer", "Plot 4");
        f.setMarks("ZK 2", null, null);
        assertEquals("J Farmer", f.getOwnerName());
        assertEquals("ZK 2", f.getBrandMark());
    }
}
