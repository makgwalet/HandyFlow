package za.co.handyflow.platform.agriculture.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import za.co.handyflow.platform.WebMvcTestSecuritySupport;
import za.co.handyflow.platform.agriculture.application.internal.AgAnimalService;
import za.co.handyflow.platform.agriculture.application.internal.AgBreedingRecordService;
import za.co.handyflow.platform.agriculture.application.internal.AgFeedRecordService;
import za.co.handyflow.platform.agriculture.application.internal.AgHealthEventService;
import za.co.handyflow.platform.agriculture.application.internal.AgMortalityRecordService;
import za.co.handyflow.platform.agriculture.application.internal.AgMovementRecordService;
import za.co.handyflow.platform.agriculture.dto.AnimalResponse;
import za.co.handyflow.platform.agriculture.dto.HealthEventResponse;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.evidence.application.EvidenceFacade;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Covers both the animal CRUD path and a nested sub-resource
 * (health-event acknowledge, addressed by record id alone — see
 * AgAnimalController's own class Javadoc for why that endpoint lives here).
 */
@WebMvcTest(AgAnimalController.class)
@Import(WebMvcTestSecuritySupport.class)
class AgAnimalControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    @MockitoBean AgAnimalService animalService;
    @MockitoBean AgHealthEventService healthEventService;
    @MockitoBean AgBreedingRecordService breedingRecordService;
    @MockitoBean AgMovementRecordService movementRecordService;
    @MockitoBean AgMortalityRecordService mortalityRecordService;
    @MockitoBean AgFeedRecordService feedRecordService;
    @MockitoBean EvidenceFacade evidenceFacade;
    @MockitoBean FeatureGuard featureGuard;

    static final String BASE = "/api/v1/agriculture";

    AnimalResponse animalResponse(UUID id) {
        return new AnimalResponse(id, UUID.randomUUID(), null, null, UUID.randomUUID(), "T-1042", "Bella",
                "Bonsmara", "FEMALE", LocalDate.of(2023, 3, 1), false, null, null, "BORN_ON_FARM",
                LocalDate.of(2023, 3, 1), null, null, "ACTIVE", null, Instant.now(), Instant.now());
    }

    HealthEventResponse healthEventResponse(UUID id) {
        return new HealthEventResponse(id, UUID.randomUUID(), null, "VACCINATION", LocalDate.now(),
                "Annual booster", null, null, null, null, null, null, null, null, true,
                "COMPLETED", null, Instant.now(), Instant.now());
    }

    @Test
    @WithMockUser(authorities = "AGRICULTURE_READ")
    @DisplayName("GET /animals/{id} returns 200 and calls featureGuard.requireModule(\"agriculture\")")
    void getAnimalReturns200AndChecksFeatureGuard() throws Exception {
        UUID id = UUID.randomUUID();
        when(animalService.getAnimal(any(), any())).thenReturn(animalResponse(id));

        mvc.perform(get(BASE + "/animals/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tagNumber").value("T-1042"));

        verify(featureGuard, atLeastOnce()).requireModule("agriculture");
    }

    @Test
    @WithMockUser(authorities = "AGRICULTURE_READ")
    @DisplayName("PATCH /animals/{id}/status with only AGRICULTURE_READ returns 403")
    void changeStatusWithReadOnlyReturns403() throws Exception {
        UUID id = UUID.randomUUID();
        mvc.perform(patch(BASE + "/animals/" + id + "/status").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"SOLD\"}"))
                .andExpect(status().isForbidden());

        verify(animalService, never()).changeStatus(any(), any(), any());
    }

    @Test
    @WithMockUser(authorities = "AGRICULTURE_MANAGE")
    @DisplayName("PATCH /health-events/{id}/acknowledge succeeds regardless of animal-vs-group linkage")
    void acknowledgeHealthEventSucceeds() throws Exception {
        UUID eventId = UUID.randomUUID();
        when(healthEventService.acknowledgeReminder(any(), any())).thenReturn(healthEventResponse(eventId));

        mvc.perform(patch(BASE + "/health-events/" + eventId + "/acknowledge").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reminderAcknowledged").value(true));

        verify(featureGuard, atLeastOnce()).requireModule("agriculture");
        verify(healthEventService).acknowledgeReminder(any(), org.mockito.ArgumentMatchers.eq(eventId));
    }

    @Test
    @WithMockUser(authorities = "AGRICULTURE_ADMIN")
    @DisplayName("DELETE /animals/{id} with AGRICULTURE_ADMIN succeeds")
    void deleteAnimalWithAdminSucceeds() throws Exception {
        UUID id = UUID.randomUUID();
        mvc.perform(delete(BASE + "/animals/" + id).with(csrf()))
                .andExpect(status().isOk());

        verify(animalService).deleteAnimal(any(), org.mockito.ArgumentMatchers.eq(id));
    }

    @Test
    @WithMockUser(authorities = "AGRICULTURE_MANAGE")
    @DisplayName("DELETE /animals/{id} with only AGRICULTURE_MANAGE (no ADMIN) returns 403")
    void deleteAnimalWithManageOnlyReturns403() throws Exception {
        mvc.perform(delete(BASE + "/animals/" + UUID.randomUUID()).with(csrf()))
                .andExpect(status().isForbidden());

        verify(animalService, never()).deleteAnimal(any(), any());
    }
}
