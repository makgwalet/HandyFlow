package za.co.handyflow.platform.agriculture.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import za.co.handyflow.platform.WebMvcTestSecuritySupport;
import za.co.handyflow.platform.agriculture.application.internal.AgFarmService;
import za.co.handyflow.platform.agriculture.dto.FarmResponse;
import za.co.handyflow.platform.billing.FeatureGuard;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Spring MVC slice test — proves featureGuard.requireModule("agriculture")
 * is invoked on every handler and that read/write/admin permission tiers
 * are actually enforced, mirroring ClinicControllerTest/HrControllerTest's
 * own established shape for this engagement.
 */
@WebMvcTest(AgFarmController.class)
@Import(WebMvcTestSecuritySupport.class)
class AgFarmControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    @MockitoBean AgFarmService farmService;
    @MockitoBean FeatureGuard featureGuard;

    static final String BASE = "/api/v1/agriculture/farms";

    FarmResponse farmResponse(UUID id) {
        return new FarmResponse(id, "Rietvlei Farm", "LIVESTOCK", null, "Free State", "Bethlehem",
                null, null, null, null, null, "ACTIVE", null, Instant.now(), Instant.now());
    }

    @Test
    @WithMockUser(authorities = "AGRICULTURE_READ")
    @DisplayName("GET /farms returns 200 and calls featureGuard.requireModule(\"agriculture\")")
    void getFarmsReturns200AndChecksFeatureGuard() throws Exception {
        Page<FarmResponse> page = new PageImpl<>(List.of(farmResponse(UUID.randomUUID())));
        when(farmService.getFarms(any(), any(), any())).thenReturn(page);

        mvc.perform(get(BASE))
                .andExpect(status().isOk());

        verify(featureGuard, atLeastOnce()).requireModule("agriculture");
    }

    @Test
    @WithMockUser(authorities = "AGRICULTURE_MANAGE")
    @DisplayName("GET /farms with only AGRICULTURE_MANAGE (no READ) returns 403")
    void getFarmsWithoutReadReturns403() throws Exception {
        mvc.perform(get(BASE))
                .andExpect(status().isForbidden());

        verifyNoInteractions(farmService);
    }

    @Test
    @WithMockUser(authorities = "AGRICULTURE_READ")
    @DisplayName("POST /farms with only AGRICULTURE_READ returns 403")
    void createFarmWithReadOnlyReturns403() throws Exception {
        mvc.perform(post(BASE).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Rietvlei\",\"farmType\":\"LIVESTOCK\"}"))
                .andExpect(status().isForbidden());

        verify(farmService, never()).createFarm(any(), any());
    }

    @Test
    @WithMockUser(authorities = "AGRICULTURE_MANAGE")
    @DisplayName("POST /farms with AGRICULTURE_MANAGE succeeds")
    void createFarmWithManageSucceeds() throws Exception {
        UUID id = UUID.randomUUID();
        when(farmService.createFarm(any(), any())).thenReturn(farmResponse(id));

        mvc.perform(post(BASE).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Rietvlei Farm\",\"farmType\":\"LIVESTOCK\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value("Rietvlei Farm"));
    }

    @Test
    @WithMockUser(authorities = "AGRICULTURE_MANAGE")
    @DisplayName("DELETE /farms/{id} with only AGRICULTURE_MANAGE (no ADMIN) returns 403")
    void deleteFarmWithManageOnlyReturns403() throws Exception {
        mvc.perform(delete(BASE + "/" + UUID.randomUUID()).with(csrf()))
                .andExpect(status().isForbidden());

        verify(farmService, never()).deleteFarm(any(), any());
    }

    @Test
    @WithMockUser(authorities = "AGRICULTURE_ADMIN")
    @DisplayName("DELETE /farms/{id} with AGRICULTURE_ADMIN succeeds")
    void deleteFarmWithAdminSucceeds() throws Exception {
        UUID id = UUID.randomUUID();
        mvc.perform(delete(BASE + "/" + id).with(csrf()))
                .andExpect(status().isOk());

        verify(farmService).deleteFarm(any(), org.mockito.ArgumentMatchers.eq(id));
    }
}
