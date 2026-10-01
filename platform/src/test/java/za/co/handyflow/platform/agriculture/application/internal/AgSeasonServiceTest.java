package za.co.handyflow.platform.agriculture.application.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import za.co.handyflow.platform.agriculture.domain.model.AgCropCycle;
import za.co.handyflow.platform.agriculture.domain.model.AgSeason;
import za.co.handyflow.platform.agriculture.domain.repository.AgCropCycleRepository;
import za.co.handyflow.platform.agriculture.domain.repository.AgFarmRepository;
import za.co.handyflow.platform.agriculture.domain.repository.AgSeasonRepository;
import za.co.handyflow.platform.agriculture.dto.CreateSeasonRequest;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AgSeasonServiceTest {

    @Mock AgSeasonRepository seasonRepository;
    @Mock AgFarmRepository farmRepository;
    @Mock AgCropCycleRepository cropCycleRepository;

    static final TenantId TENANT = TenantId.of(UUID.randomUUID());
    final UUID farmId = UUID.randomUUID();
    final UUID seasonId = UUID.randomUUID();

    private AgSeasonService service() {
        return new AgSeasonService(seasonRepository, farmRepository, cropCycleRepository);
    }

    private AgSeason existingSeason() {
        AgSeason season = AgSeason.create(TENANT, farmId, "2026/27 summer", LocalDate.of(2026, 10, 1), null, null);
        when(seasonRepository.findActiveById(eq(TENANT), eq(seasonId))).thenReturn(Optional.of(season));
        return season;
    }

    @Test
    @DisplayName("a season still used by crop cycles cannot be deleted")
    void deleteRefusedWhileCyclesAreLinked() {
        AgSeason season = existingSeason();
        when(cropCycleRepository.findAllActiveForSeason(eq(TENANT), eq(seasonId), any()))
                .thenReturn(new PageImpl<AgCropCycle>(List.of(), PageRequest.of(0, 1), 3));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> service().deleteSeason(TENANT, seasonId));

        assertTrue(ex.getMessage().contains("3 crop cycle(s)"));
        assertFalse(season.isDeleted());
    }

    @Test
    @DisplayName("an unused season is deleted")
    void deleteWorksWithoutCycles() {
        AgSeason season = existingSeason();
        when(cropCycleRepository.findAllActiveForSeason(eq(TENANT), eq(seasonId), any()))
                .thenReturn(new PageImpl<AgCropCycle>(List.of()));

        service().deleteSeason(TENANT, seasonId);

        assertTrue(season.isDeleted());
    }

    @Test
    @DisplayName("creating a season needs a farm that exists in this tenant")
    void createRequiresFarm() {
        CreateSeasonRequest req = new CreateSeasonRequest(farmId, "2026/27 summer", LocalDate.of(2026, 10, 1), null, null);

        assertThrows(ResourceNotFoundException.class, () -> service().createSeason(TENANT, req));
        verify(seasonRepository, never()).save(any(AgSeason.class));
    }

    @Test
    @DisplayName("activating twice is refused, and so is closing twice")
    void transitionsAreGuarded() {
        existingSeason();

        service().activateSeason(TENANT, seasonId);
        assertThrows(IllegalStateException.class, () -> service().activateSeason(TENANT, seasonId));

        service().closeSeason(TENANT, seasonId);
        assertThrows(IllegalStateException.class, () -> service().closeSeason(TENANT, seasonId));
    }
}
