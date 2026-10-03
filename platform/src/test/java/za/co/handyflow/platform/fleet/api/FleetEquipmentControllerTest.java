package za.co.handyflow.platform.fleet.api;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.ResultActions;
import za.co.handyflow.platform.WebMvcTestSecuritySupport;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.fleet.application.internal.FleetCostService;
import za.co.handyflow.platform.fleet.application.internal.FleetLogbookService;
import za.co.handyflow.platform.fleet.application.internal.FleetService;
import za.co.handyflow.platform.fleet.dto.EquipmentResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FleetController.class)
@Import(WebMvcTestSecuritySupport.class)
class FleetEquipmentControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean FleetService fleetService;
    @MockitoBean FleetLogbookService logbookService;
    @MockitoBean FleetCostService costService;
    @MockitoBean FeatureGuard featureGuard;

    final UUID vehicleId = UUID.randomUUID();

    // JwtAuthFilter clears TenantContext after every request, so the tenant is seeded immediately before each one.
    private ResultActions call(RequestBuilder request) throws Exception {
        TenantContext.setTenantId(UUID.randomUUID().toString());
        TenantContext.setUserId(UUID.randomUUID().toString());
        return mvc.perform(request);
    }

    @AfterEach
    void clearTenantContext() { TenantContext.clear(); }

    private static final String BODY = "{\"engineHours\":1234.5,\"operatingRatePerHour\":85.5}";

    @Test
    @WithMockUser(authorities = "FLEET_READ")
    @DisplayName("GET /equipment lists the machines with their meter and operating rate, and checks the fleet module")
    void lists() throws Exception {
        when(fleetService.listEquipment(any())).thenReturn(List.of(new EquipmentResponse(vehicleId, "FARM 001 GP", "John Deere", "6120M", "TRACTOR", "AVAILABLE", new BigDecimal("1234.5"), new BigDecimal("85.5"))));

        call(get("/api/v1/fleet/equipment")).andExpect(status().isOk()).andExpect(jsonPath("$.data[0].operatingRatePerHour").value(85.5));

        verify(featureGuard, atLeastOnce()).requireModule("fleet");
    }

    @Test
    @WithMockUser(authorities = "FLEET_MANAGE")
    @DisplayName("PATCH /vehicles/{id}/equipment saves the numbers")
    void updates() throws Exception {
        when(fleetService.updateEquipment(any(), eq(vehicleId), any())).thenReturn(new EquipmentResponse(vehicleId, "FARM 001 GP", "John Deere", "6120M", "TRACTOR", "AVAILABLE", new BigDecimal("1234.5"), new BigDecimal("85.5")));

        call(patch("/api/v1/fleet/vehicles/" + vehicleId + "/equipment").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isOk());

        verify(fleetService).updateEquipment(any(), eq(vehicleId), any());
    }

    @Test
    @WithMockUser(authorities = "FLEET_READ")
    @DisplayName("reading the fleet is not enough to change a machine's rate: that needs FLEET_MANAGE")
    void updateNeedsManage() throws Exception {
        call(patch("/api/v1/fleet/vehicles/" + vehicleId + "/equipment").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isForbidden());

        verify(fleetService, never()).updateEquipment(any(), any(), any());
    }

    @Test
    @WithMockUser(authorities = "AGRICULTURE_FINANCE")
    @DisplayName("an Agriculture finance user without Fleet rights cannot list the fleet's equipment")
    void listNeedsFleetRead() throws Exception {
        call(get("/api/v1/fleet/equipment")).andExpect(status().isForbidden());

        verify(fleetService, never()).listEquipment(any());
    }
}
