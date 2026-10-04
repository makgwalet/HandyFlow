package za.co.handyflow.platform.agriculture.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AgAnimalBreedingStockTest {

    private static AgAnimal bull() {
        return AgAnimal.create(TenantId.of(UUID.randomUUID()), UUID.randomUUID(), null, null, UUID.randomUUID(), "T-1042", "Samson", "Bonsmara", "MALE",
                LocalDate.of(2022, 3, 1), false, null, null, "PURCHASED", LocalDate.of(2024, 1, 10), new BigDecimal("45000"));
    }

    @Test
    @DisplayName("an animal is not breeding stock until someone flags it, so existing animals are reported exactly as before")
    void defaultsToNotBreedingStock() {
        assertFalse(bull().isBreedingStock());
    }

    @Test
    @DisplayName("it can be flagged and unflagged")
    void flagsAndUnflags() {
        AgAnimal a = bull();

        a.markBreedingStock(true);
        assertTrue(a.isBreedingStock());

        a.markBreedingStock(false);
        assertFalse(a.isBreedingStock());
    }

    @Test
    @DisplayName("flagging changes nothing else: not the purchase price, the status or the identity")
    void changesNothingElse() {
        AgAnimal a = bull();

        a.markBreedingStock(true);

        assertEquals(0, new BigDecimal("45000").compareTo(a.getAcquisitionCost()));
        assertEquals("ACTIVE", a.getStatus());
        assertEquals("T-1042", a.getTagNumber());
        assertEquals("PURCHASED", a.getAcquisitionType());
    }
}
