package za.co.handyflow.platform.agriculture.application.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import za.co.handyflow.platform.agriculture.domain.model.AgAnimal;
import za.co.handyflow.platform.agriculture.domain.repository.AgAnimalRepository;
import za.co.handyflow.platform.agriculture.domain.repository.AgWeightRecordRepository;
import za.co.handyflow.platform.agriculture.dto.AnimalResponse;
import za.co.handyflow.platform.hr.application.HrFacade;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AgAnimalServiceBreedingStockTest {

    @Mock AgAnimalRepository animalRepository;
    @Mock AgWeightRecordRepository weightRecordRepository;
    static final TenantId TENANT = TenantId.of(UUID.randomUUID());

    private AgAnimalService service() { return new AgAnimalService(animalRepository, weightRecordRepository, mock(HrFacade.class)); }

    private AgAnimal bull() {
        AgAnimal a = AgAnimal.create(TENANT, UUID.randomUUID(), null, null, UUID.randomUUID(), "T-1042", "Samson", "Bonsmara", "MALE",
                LocalDate.of(2022, 3, 1), false, null, null, "PURCHASED", LocalDate.of(2024, 1, 10), new BigDecimal("45000"));
        when(animalRepository.findActiveById(eq(TENANT), eq(a.getId()))).thenReturn(Optional.of(a));
        return a;
    }

    @Test
    @DisplayName("flagging returns the animal as breeding stock, and unflagging returns it to normal")
    void flagsAndUnflags() {
        AgAnimal a = bull();

        AnimalResponse on = service().setBreedingStock(TENANT, a.getId(), true);
        assertTrue(on.breedingStock());
        assertTrue(a.isBreedingStock());

        AnimalResponse off = service().setBreedingStock(TENANT, a.getId(), false);
        assertFalse(off.breedingStock());
    }

    @Test
    @DisplayName("the purchase price is untouched by flagging")
    void priceUntouched() {
        AgAnimal a = bull();

        AnimalResponse r = service().setBreedingStock(TENANT, a.getId(), true);

        assertEquals(0, new BigDecimal("45000").compareTo(r.acquisitionCost()));
    }

    @Test
    @DisplayName("an unknown or deleted animal is a 404")
    void unknown() {
        assertThrows(ResourceNotFoundException.class, () -> service().setBreedingStock(TENANT, UUID.randomUUID(), true));
    }
}
