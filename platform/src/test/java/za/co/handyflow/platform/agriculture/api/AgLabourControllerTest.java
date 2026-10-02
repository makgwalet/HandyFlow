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
import za.co.handyflow.platform.agriculture.application.internal.AgFinanceSettingsService;
import za.co.handyflow.platform.agriculture.application.internal.AgLabourCostService;
import za.co.handyflow.platform.agriculture.dto.LabourDtos.FinanceSettingsResponse;
import za.co.handyflow.platform.agriculture.dto.LabourDtos.LabourOverview;
import za.co.handyflow.platform.billing.FeatureGuard;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AgLabourController.class)
@Import(WebMvcTestSecuritySupport.class)
class AgLabourControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean AgLabourCostService labourService;
    @MockitoBean AgFinanceSettingsService settingsService;
    @MockitoBean FeatureGuard featureGuard;

    static final String BASE = "/api/v1/agriculture";
    final UUID farmId = UUID.randomUUID();
    final UUID workId = UUID.randomUUID();

    // The handlers read the tenant from TenantContext, which the JWT filter normally fills and then clears after every request. This slice has
    // no real login, so TenantRequests seeds it right before each request; clearing it here keeps it from leaking into other test classes.
    @AfterEach
    void clearTenantContext() {
        TenantRequests.clear();
    }

    private LabourOverview overview(boolean hr) {
        return new LabourOverview(new FinanceSettingsResponse(new BigDecimal("45.00"), new BigDecimal("2.00"), true), hr, List.of());
    }

    private String costBody() {
        return "{\"items\":[{\"sourceType\":\"INPUT_APPLICATION\",\"sourceId\":\"" + workId + "\",\"hourlyRate\":80}]}";
    }

    @Test
    @WithMockUser(authorities = "AGRICULTURE_FINANCE")
    @DisplayName("GET /finance/settings returns the settings and checks the module is enabled")
    void getSettings() throws Exception {
        when(settingsService.get(any())).thenReturn(new FinanceSettingsResponse(new BigDecimal("45.00"), new BigDecimal("0.00"), false));

        TenantRequests.asTenant(mvc, get(BASE + "/finance/settings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.standardHoursPerWeek").value(45.0))
                .andExpect(jsonPath("$.data.configured").value(false));

        verify(featureGuard, atLeastOnce()).requireModule("agriculture");
    }

    @Test
    @WithMockUser(authorities = "AGRICULTURE_FINANCE")
    @DisplayName("PUT /finance/settings saves them; a body missing a number is a 400 and never reaches the service")
    void updateSettings() throws Exception {
        when(settingsService.update(any(), any(), any())).thenReturn(new FinanceSettingsResponse(new BigDecimal("40.00"), new BigDecimal("2.00"), true));

        TenantRequests.asTenant(mvc, put(BASE + "/finance/settings").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"standardHoursPerWeek\":40,\"labourOnCostPercent\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.labourOnCostPercent").value(2.0));
        verify(settingsService).update(any(), any(), any());

        clearInvocations(settingsService);
        TenantRequests.asTenant(mvc, put(BASE + "/finance/settings").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"standardHoursPerWeek\":40}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(settingsService);
    }

    @Test
    @WithMockUser(authorities = "AGRICULTURE_FINANCE")
    @DisplayName("with AGRICULTURE_FINANCE alone, the uncosted list is built WITHOUT HR rates")
    void uncostedWithoutHrAccess() throws Exception {
        when(labourService.overview(any(), eq(farmId), eq(false))).thenReturn(overview(false));

        TenantRequests.asTenant(mvc, get(BASE + "/farms/" + farmId + "/labour/uncosted"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hrRatesAvailable").value(false));

        verify(labourService).overview(any(), eq(farmId), eq(false));
    }

    @Test
    @WithMockUser(authorities = {"AGRICULTURE_FINANCE", "HR_READ"})
    @DisplayName("HR_READ switches HR rates on")
    void uncostedWithHrRead() throws Exception {
        when(labourService.overview(any(), eq(farmId), eq(true))).thenReturn(overview(true));

        TenantRequests.asTenant(mvc, get(BASE + "/farms/" + farmId + "/labour/uncosted")).andExpect(status().isOk()).andExpect(jsonPath("$.data.hrRatesAvailable").value(true));

        verify(labourService).overview(any(), eq(farmId), eq(true));
    }

    @Test
    @WithMockUser(authorities = {"AGRICULTURE_FINANCE", "HR_MANAGE"})
    @DisplayName("HR_MANAGE switches HR rates on")
    void uncostedWithHrManage() throws Exception {
        when(labourService.overview(any(), eq(farmId), eq(true))).thenReturn(overview(true));

        TenantRequests.asTenant(mvc, get(BASE + "/farms/" + farmId + "/labour/uncosted")).andExpect(status().isOk());

        verify(labourService).overview(any(), eq(farmId), eq(true));
    }

    @Test
    @WithMockUser(authorities = {"AGRICULTURE_FINANCE", "USER_READ"})
    @DisplayName("USER_READ, which HR itself accepts for reading an employee, switches HR rates on")
    void uncostedWithUserRead() throws Exception {
        when(labourService.overview(any(), eq(farmId), eq(true))).thenReturn(overview(true));

        TenantRequests.asTenant(mvc, get(BASE + "/farms/" + farmId + "/labour/uncosted")).andExpect(status().isOk());

        verify(labourService).overview(any(), eq(farmId), eq(true));
    }

    @Test
    @WithMockUser(authorities = {"AGRICULTURE_FINANCE", "AGRICULTURE_READ", "AGRICULTURE_MANAGE", "AGRICULTURE_ADMIN", "INVOICE_READ"})
    @DisplayName("other permissions, even broad Agriculture ones, do not switch HR rates on")
    void otherPermissionsDoNot() throws Exception {
        when(labourService.overview(any(), eq(farmId), eq(false))).thenReturn(overview(false));

        TenantRequests.asTenant(mvc, get(BASE + "/farms/" + farmId + "/labour/uncosted")).andExpect(status().isOk());

        verify(labourService).overview(any(), eq(farmId), eq(false));
    }

    @Test
    @WithMockUser(authorities = "AGRICULTURE_FINANCE")
    @DisplayName("POST /labour/cost returns 201, passing the caller's HR access (false here) to the service")
    void costWithoutHrAccess() throws Exception {
        when(labourService.cost(any(), eq(farmId), any(), eq(false), any())).thenReturn(List.of());

        TenantRequests.asTenant(mvc, post(BASE + "/farms/" + farmId + "/labour/cost").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(costBody()))
                .andExpect(status().isCreated());

        verify(labourService).cost(any(), eq(farmId), any(), eq(false), any());
    }

    @Test
    @WithMockUser(authorities = {"AGRICULTURE_FINANCE", "HR_READ"})
    @DisplayName("POST /labour/cost with HR access tells the service so")
    void costWithHrAccess() throws Exception {
        when(labourService.cost(any(), eq(farmId), any(), eq(true), any())).thenReturn(List.of());

        TenantRequests.asTenant(mvc, post(BASE + "/farms/" + farmId + "/labour/cost").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(costBody()))
                .andExpect(status().isCreated());

        verify(labourService).cost(any(), eq(farmId), any(), eq(true), any());
    }

    @Test
    @WithMockUser(authorities = "AGRICULTURE_FINANCE")
    @DisplayName("POST /labour/cost with no items, an item with no id, or a non-positive rate is a 400 and never reaches the service")
    void costValidatesTheRequest() throws Exception {
        TenantRequests.asTenant(mvc, post(BASE + "/farms/" + farmId + "/labour/cost").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isBadRequest());
        TenantRequests.asTenant(mvc, post(BASE + "/farms/" + farmId + "/labour/cost").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"items\":[{\"sourceType\":\"HARVEST\"}]}")).andExpect(status().isBadRequest());
        TenantRequests.asTenant(mvc, post(BASE + "/farms/" + farmId + "/labour/cost").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"items\":[{\"sourceType\":\"HARVEST\",\"sourceId\":\"" + workId + "\",\"hourlyRate\":0}]}")).andExpect(status().isBadRequest());
        verifyNoInteractions(labourService);
    }

    @Test
    @WithMockUser(authorities = {"HR_READ", "HR_MANAGE", "USER_READ", "AGRICULTURE_READ", "AGRICULTURE_MANAGE", "AGRICULTURE_ADMIN"})
    @DisplayName("without AGRICULTURE_FINANCE every labour endpoint is forbidden, whatever else the caller holds, even HR rights")
    void financeIsRequired() throws Exception {
        TenantRequests.asTenant(mvc, get(BASE + "/finance/settings")).andExpect(status().isForbidden());
        TenantRequests.asTenant(mvc, put(BASE + "/finance/settings").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"standardHoursPerWeek\":40,\"labourOnCostPercent\":2}")).andExpect(status().isForbidden());
        TenantRequests.asTenant(mvc, get(BASE + "/farms/" + farmId + "/labour/uncosted")).andExpect(status().isForbidden());
        TenantRequests.asTenant(mvc, post(BASE + "/farms/" + farmId + "/labour/cost").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(costBody())).andExpect(status().isForbidden());
        verifyNoInteractions(labourService);
        verifyNoInteractions(settingsService);
    }
}
