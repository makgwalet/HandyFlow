package za.co.handyflow.platform.agriculture.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import za.co.handyflow.platform.WebMvcTestSecuritySupport;
import za.co.handyflow.platform.agriculture.application.internal.AgCropCycleService;
import za.co.handyflow.platform.agriculture.application.internal.AgHarvestRecordService;
import za.co.handyflow.platform.agriculture.application.internal.AgInputApplicationService;
import za.co.handyflow.platform.agriculture.application.internal.AgScoutingRecordService;
import za.co.handyflow.platform.agriculture.dto.CropCycleResponse;
import za.co.handyflow.platform.agriculture.dto.ScoutingRecordResponse;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.evidence.application.EvidenceFacade;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Covers both the crop-cycle CRUD path and a nested sub-resource
 * (scouting-record acknowledge-follow-up, addressed by record id alone —
 * see AgCropCycleController's own class Javadoc). Mirrors
 * AgAnimalControllerTest's own shape.
 */
@WebMvcTest(AgCropCycleController.class)
@Import(WebMvcTestSecuritySupport.class)
class AgCropCycleControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    @MockitoBean AgCropCycleService cropCycleService;
    @MockitoBean AgInputApplicationService inputApplicationService;
    @MockitoBean AgScoutingRecordService scoutingRecordService;
    @MockitoBean AgHarvestRecordService harvestRecordService;
    @MockitoBean EvidenceFacade evidenceFacade;
    @MockitoBean FeatureGuard featureGuard;

    static final String BASE = "/api/v1/agriculture";

    CropCycleResponse cropCycleResponse(UUID id) {
        return new CropCycleResponse(id, UUID.randomUUID(), UUID.randomUUID(), null, null, UUID.randomUUID(),
                "Yellow Dent", "Field 3 - 2026 Summer", new BigDecimal("12.50"), LocalDate.now(),
                LocalDate.now().plusMonths(4), null, null, null, "PLANTED", null, Instant.now(), Instant.now());
    }

    ScoutingRecordResponse scoutingRecordResponse(UUID id) {
        return new ScoutingRecordResponse(id, UUID.randomUUID(), LocalDate.now(), "PEST", "MEDIUM",
                "Aphids observed", "Spray next week", null, null, LocalDate.now().plusDays(7), true,
                "OPEN", null, Instant.now(), Instant.now());
    }

    @Test
    @WithMockUser(authorities = "AGRICULTURE_READ")
    @DisplayName("GET /crop-cycles/{id} returns 200 and calls featureGuard.requireModule(\"agriculture\")")
    void getCropCycleReturns200AndChecksFeatureGuard() throws Exception {
        UUID id = UUID.randomUUID();
        when(cropCycleService.getCropCycle(any(), any())).thenReturn(cropCycleResponse(id));

        mvc.perform(get(BASE + "/crop-cycles/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PLANTED"));

        verify(featureGuard, atLeastOnce()).requireModule("agriculture");
    }

    @Test
    @WithMockUser(authorities = "AGRICULTURE_READ")
    @DisplayName("PATCH /crop-cycles/{id}/mark-growing with only AGRICULTURE_READ returns 403")
    void markGrowingWithReadOnlyReturns403() throws Exception {
        UUID id = UUID.randomUUID();
        mvc.perform(patch(BASE + "/crop-cycles/" + id + "/mark-growing").with(csrf()))
                .andExpect(status().isForbidden());

        verify(cropCycleService, never()).markGrowing(any(), any());
    }

    @Test
    @WithMockUser(authorities = "AGRICULTURE_MANAGE")
    @DisplayName("PATCH /scouting-records/{id}/acknowledge-follow-up succeeds regardless of the owning crop cycle")
    void acknowledgeFollowUpSucceeds() throws Exception {
        UUID recordId = UUID.randomUUID();
        when(scoutingRecordService.acknowledgeFollowUp(any(), any())).thenReturn(scoutingRecordResponse(recordId));

        mvc.perform(patch(BASE + "/scouting-records/" + recordId + "/acknowledge-follow-up").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.followUpAcknowledged").value(true));

        verify(featureGuard, atLeastOnce()).requireModule("agriculture");
        verify(scoutingRecordService).acknowledgeFollowUp(any(), org.mockito.ArgumentMatchers.eq(recordId));
    }

    @Test
    @WithMockUser(authorities = "AGRICULTURE_ADMIN")
    @DisplayName("DELETE /crop-cycles/{id} with AGRICULTURE_ADMIN succeeds")
    void deleteCropCycleWithAdminSucceeds() throws Exception {
        UUID id = UUID.randomUUID();
        mvc.perform(delete(BASE + "/crop-cycles/" + id).with(csrf()))
                .andExpect(status().isOk());

        verify(cropCycleService).deleteCropCycle(any(), org.mockito.ArgumentMatchers.eq(id));
    }

    @Test
    @WithMockUser(authorities = "AGRICULTURE_MANAGE")
    @DisplayName("DELETE /crop-cycles/{id} with only AGRICULTURE_MANAGE (no ADMIN) returns 403")
    void deleteCropCycleWithManageOnlyReturns403() throws Exception {
        mvc.perform(delete(BASE + "/crop-cycles/" + UUID.randomUUID()).with(csrf()))
                .andExpect(status().isForbidden());

        verify(cropCycleService, never()).deleteCropCycle(any(), any());
    }
}
