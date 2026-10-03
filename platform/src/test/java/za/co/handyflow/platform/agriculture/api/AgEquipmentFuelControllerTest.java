package za.co.handyflow.platform.agriculture.api;

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
import za.co.handyflow.platform.WebMvcTestSecuritySupport;
import za.co.handyflow.platform.agriculture.application.internal.AgEquipmentCostService;
import za.co.handyflow.platform.agriculture.application.internal.AgFuelCostService;
import za.co.handyflow.platform.agriculture.dto.EquipmentFuelDtos.EquipmentOption;
import za.co.handyflow.platform.agriculture.dto.EquipmentFuelDtos.FuelOverview;
import za.co.handyflow.platform.billing.FeatureGuard;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AgEquipmentFuelController.class)
@Import(WebMvcTestSecuritySupport.class)
class AgEquipmentFuelControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean AgEquipmentCostService equipmentService;
    @MockitoBean AgFuelCostService fuelService;
    @MockitoBean FeatureGuard featureGuard;

    static final String BASE = "/api/v1/agriculture";
    final UUID farmId = UUID.randomUUID(), vehicleId = UUID.randomUUID(), cycleId = UUID.randomUUID(), dispatchId = UUID.randomUUID();

    // The handlers read the tenant from TenantContext, which the JWT filter normally fills and then clears after every request. This slice has
    // no real login, so TenantRequests seeds it right before each request; clearing it here keeps it from leaking into other test classes.
    @AfterEach
    void clearTenantContext() {
        TenantRequests.clear();
    }

    private String equipmentBody(String hours) {
        return "{\"vehicleId\":\"" + vehicleId + "\",\"workDate\":\"2026-09-10\",\"hours\":" + hours + ",\"allocations\":[{\"targetType\":\"CROP_CYCLE\",\"targetId\":\"" + cycleId + "\",\"percentage\":100}]}";
    }

    private String fuelBody() {
        return "{\"dispatchId\":\"" + dispatchId + "\",\"allocations\":[{\"targetType\":\"CROP_CYCLE\",\"targetId\":\"" + cycleId + "\",\"percentage\":100}]}";
    }

    // ---- equipment ----------------------------------------------------------------------------------------------------

    @Test
    @WithMockUser(authorities = {"AGRICULTURE_FINANCE", "FLEET_READ"})
    @DisplayName("GET /finance/equipment lists the machines and checks both modules are enabled")
    void listsEquipment() throws Exception {
        when(equipmentService.equipment(any())).thenReturn(List.of(new EquipmentOption(vehicleId, "FARM 001 GP", "FARM 001 GP (John Deere 6120M)", new BigDecimal("1234.5"), new BigDecimal("85.5"))));

        TenantRequests.asTenant(mvc, get(BASE + "/finance/equipment"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].registration").value("FARM 001 GP"))
                .andExpect(jsonPath("$.data[0].operatingRatePerHour").value(85.5));

        verify(featureGuard, atLeastOnce()).requireModule("agriculture");
        verify(featureGuard, atLeastOnce()).requireModule("fleet");
    }

    @Test
    @WithMockUser(authorities = {"AGRICULTURE_FINANCE", "FLEET_READ"})
    @DisplayName("POST /equipment/cost returns 201; a body with no hours, zero hours or no targets is a 400 and never reaches the service")
    void costsEquipment() throws Exception {
        when(equipmentService.costUse(any(), eq(farmId), any(), any())).thenReturn(List.of());

        TenantRequests.asTenant(mvc, post(BASE + "/farms/" + farmId + "/equipment/cost").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(equipmentBody("6")))
                .andExpect(status().isCreated());
        verify(equipmentService).costUse(any(), eq(farmId), any(), any());

        clearInvocations(equipmentService);
        TenantRequests.asTenant(mvc, post(BASE + "/farms/" + farmId + "/equipment/cost").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(equipmentBody("0"))).andExpect(status().isBadRequest());
        TenantRequests.asTenant(mvc, post(BASE + "/farms/" + farmId + "/equipment/cost").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"vehicleId\":\"" + vehicleId + "\",\"workDate\":\"2026-09-10\"}")).andExpect(status().isBadRequest());
        TenantRequests.asTenant(mvc, post(BASE + "/farms/" + farmId + "/equipment/cost").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isBadRequest());
        verifyNoInteractions(equipmentService);
    }

    @Test
    @WithMockUser(authorities = "AGRICULTURE_FINANCE")
    @DisplayName("equipment needs FLEET_READ as well: finance alone is forbidden")
    void equipmentNeedsFleetRead() throws Exception {
        TenantRequests.asTenant(mvc, get(BASE + "/finance/equipment")).andExpect(status().isForbidden());
        TenantRequests.asTenant(mvc, post(BASE + "/farms/" + farmId + "/equipment/cost").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(equipmentBody("6"))).andExpect(status().isForbidden());
        verifyNoInteractions(equipmentService);
    }

    @Test
    @WithMockUser(authorities = {"FLEET_READ", "FLEET_MANAGE", "AGRICULTURE_READ", "AGRICULTURE_MANAGE", "AGRICULTURE_ADMIN"})
    @DisplayName("Fleet rights and broad Agriculture rights do not replace AGRICULTURE_FINANCE")
    void equipmentNeedsFinance() throws Exception {
        TenantRequests.asTenant(mvc, get(BASE + "/finance/equipment")).andExpect(status().isForbidden());
        TenantRequests.asTenant(mvc, post(BASE + "/farms/" + farmId + "/equipment/cost").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(equipmentBody("6"))).andExpect(status().isForbidden());
        verifyNoInteractions(equipmentService);
    }

    // ---- fuel ---------------------------------------------------------------------------------------------------------

    @Test
    @WithMockUser(authorities = {"AGRICULTURE_FINANCE", "FUEL_MARGIN_READ"})
    @DisplayName("GET /fuel/unallocated passes the dates through and checks both modules are enabled")
    void listsFuel() throws Exception {
        when(fuelService.unallocated(any(), eq(farmId), eq(LocalDate.of(2026, 9, 1)), eq(LocalDate.of(2026, 9, 30))))
                .thenReturn(new FuelOverview(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), List.of(), 3));

        TenantRequests.asTenant(mvc, get(BASE + "/farms/" + farmId + "/fuel/unallocated").param("from", "2026-09-01").param("to", "2026-09-30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.alreadyAllocated").value(3));

        verify(featureGuard, atLeastOnce()).requireModule("agriculture");
        verify(featureGuard, atLeastOnce()).requireModule("fuel");
    }

    @Test
    @WithMockUser(authorities = {"AGRICULTURE_FINANCE", "FUEL_MARGIN_READ"})
    @DisplayName("POST /fuel/allocate returns 201; a body with no dispatch or no targets is a 400")
    void allocatesFuel() throws Exception {
        when(fuelService.allocate(any(), eq(farmId), any(), any())).thenReturn(List.of());

        TenantRequests.asTenant(mvc, post(BASE + "/farms/" + farmId + "/fuel/allocate").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(fuelBody())).andExpect(status().isCreated());
        verify(fuelService).allocate(any(), eq(farmId), any(), any());

        clearInvocations(fuelService);
        TenantRequests.asTenant(mvc, post(BASE + "/farms/" + farmId + "/fuel/allocate").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isBadRequest());
        TenantRequests.asTenant(mvc, post(BASE + "/farms/" + farmId + "/fuel/allocate").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"dispatchId\":\"" + dispatchId + "\"}")).andExpect(status().isBadRequest());
        verifyNoInteractions(fuelService);
    }

    @Test
    @WithMockUser(authorities = {"AGRICULTURE_FINANCE", "FUEL_READ", "FUEL_MANAGE", "FLEET_READ"})
    @DisplayName("fuel costs need FUEL_MARGIN_READ, the permission Fuel itself uses for cost data: FUEL_READ, FUEL_MANAGE and Fleet rights are not enough")
    void fuelNeedsMarginRead() throws Exception {
        TenantRequests.asTenant(mvc, get(BASE + "/farms/" + farmId + "/fuel/unallocated")).andExpect(status().isForbidden());
        TenantRequests.asTenant(mvc, post(BASE + "/farms/" + farmId + "/fuel/allocate").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(fuelBody())).andExpect(status().isForbidden());
        verifyNoInteractions(fuelService);
    }

    @Test
    @WithMockUser(authorities = {"FUEL_MARGIN_READ", "AGRICULTURE_READ", "AGRICULTURE_MANAGE", "AGRICULTURE_ADMIN"})
    @DisplayName("FUEL_MARGIN_READ does not replace AGRICULTURE_FINANCE")
    void fuelNeedsFinance() throws Exception {
        TenantRequests.asTenant(mvc, get(BASE + "/farms/" + farmId + "/fuel/unallocated")).andExpect(status().isForbidden());
        TenantRequests.asTenant(mvc, post(BASE + "/farms/" + farmId + "/fuel/allocate").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(fuelBody())).andExpect(status().isForbidden());
        verifyNoInteractions(fuelService);
    }
}
