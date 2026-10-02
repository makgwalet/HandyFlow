package za.co.handyflow.platform.agriculture.api;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import za.co.handyflow.platform.WebMvcTestSecuritySupport;
import za.co.handyflow.platform.agriculture.application.internal.AgCostEntryService;
import za.co.handyflow.platform.agriculture.dto.CostEntryDtos.CategoryTotal;
import za.co.handyflow.platform.agriculture.dto.CostEntryDtos.CostEntryResponse;
import za.co.handyflow.platform.agriculture.dto.CostEntryDtos.CostTotalsResponse;
import za.co.handyflow.platform.billing.FeatureGuard;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AgCostEntryController.class)
@Import(WebMvcTestSecuritySupport.class)
class AgCostEntryControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean AgCostEntryService costEntryService;
    @MockitoBean FeatureGuard featureGuard;

    static final String BASE = "/api/v1/agriculture";
    final UUID farmId = UUID.randomUUID();
    final UUID targetId = UUID.randomUUID();
    final UUID groupId = UUID.randomUUID();

    @AfterEach
    void clearTenantContext() {
        TenantRequests.clear();
    }

    private CostEntryResponse row(String amount) {
        return new CostEntryResponse(UUID.randomUUID(), farmId, LocalDate.of(2026, 9, 15), "OTHER_DIRECT", "Hired sprayer", "MANUAL", null,
                "CROP_CYCLE", targetId, null, null, null, new BigDecimal(amount), new BigDecimal("100"), groupId, null, "ACTIVE", null, null, Instant.now());
    }

    private String body(String allocations) {
        return "{\"entryDate\":\"2026-09-15\",\"description\":\"Hired sprayer\",\"amount\":1250.50,\"allocations\":" + allocations + "}";
    }

    private String oneTarget() {
        return "[{\"targetType\":\"CROP_CYCLE\",\"targetId\":\"" + targetId + "\",\"percentage\":100}]";
    }

    @Test
    @WithMockUser(authorities = "AGRICULTURE_FINANCE")
    @DisplayName("POST /cost-entries records the cost, returns 201 with one row per target and checks the module")
    void createReturns201() throws Exception {
        when(costEntryService.createManual(any(), eq(farmId), any(), any())).thenReturn(List.of(row("1250.50")));

        TenantRequests.asTenant(mvc, post(BASE + "/farms/" + farmId + "/cost-entries").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body(oneTarget())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data[0].amount").value(1250.50))
                .andExpect(jsonPath("$.data[0].status").value("ACTIVE"));

        verify(featureGuard, atLeastOnce()).requireModule("agriculture");
    }

    @Test
    @WithMockUser(authorities = "AGRICULTURE_FINANCE")
    @DisplayName("POST without allocations, or with a non-positive amount, is a 400 and never reaches the service")
    void createValidatesTheRequest() throws Exception {
        TenantRequests.asTenant(mvc, post(BASE + "/farms/" + farmId + "/cost-entries").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"entryDate\":\"2026-09-15\",\"description\":\"x\",\"amount\":10}"))
                .andExpect(status().isBadRequest());
        TenantRequests.asTenant(mvc, post(BASE + "/farms/" + farmId + "/cost-entries").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"entryDate\":\"2026-09-15\",\"description\":\"x\",\"amount\":0,\"allocations\":" + oneTarget() + "}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(costEntryService);
    }

    @Test
    @WithMockUser(authorities = {"AGRICULTURE_READ", "AGRICULTURE_MANAGE", "AGRICULTURE_ADMIN"})
    @DisplayName("every ledger endpoint needs AGRICULTURE_FINANCE: READ, MANAGE and ADMIN are not enough")
    void financeIsRequired() throws Exception {
        TenantRequests.asTenant(mvc, post(BASE + "/farms/" + farmId + "/cost-entries").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body(oneTarget())))
                .andExpect(status().isForbidden());
        TenantRequests.asTenant(mvc, get(BASE + "/farms/" + farmId + "/cost-entries")).andExpect(status().isForbidden());
        TenantRequests.asTenant(mvc, get(BASE + "/farms/" + farmId + "/cost-entries/totals")).andExpect(status().isForbidden());
        TenantRequests.asTenant(mvc, post(BASE + "/cost-entries/groups/" + groupId + "/reverse").with(csrf())).andExpect(status().isForbidden());
        verifyNoInteractions(costEntryService);
    }

    @Test
    @WithMockUser(authorities = "AGRICULTURE_FINANCE")
    @DisplayName("GET /cost-entries lists a farm's rows, and passes a target filter through")
    void listPassesTheFilter() throws Exception {
        when(costEntryService.list(any(), eq(farmId), any(), any(), any())).thenReturn(new PageImpl<CostEntryResponse>(List.of(row("10"))));

        TenantRequests.asTenant(mvc, get(BASE + "/farms/" + farmId + "/cost-entries")).andExpect(status().isOk()).andExpect(jsonPath("$.data.content[0].description").value("Hired sprayer"));
        verify(costEntryService).list(any(), eq(farmId), isNull(), isNull(), any());

        TenantRequests.asTenant(mvc, get(BASE + "/farms/" + farmId + "/cost-entries").param("targetType", "GROUP").param("targetId", targetId.toString())).andExpect(status().isOk());
        verify(costEntryService).list(any(), eq(farmId), eq("GROUP"), eq(targetId), any());
    }

    @Test
    @WithMockUser(authorities = "AGRICULTURE_FINANCE")
    @DisplayName("GET /cost-entries/totals returns the net cost by category")
    void totals() throws Exception {
        when(costEntryService.totals(any(), eq(farmId), any(), any()))
                .thenReturn(new CostTotalsResponse(List.of(new CategoryTotal("LABOUR", new BigDecimal("250.50")), new CategoryTotal("OTHER_DIRECT", new BigDecimal("100"))), new BigDecimal("350.50")));

        TenantRequests.asTenant(mvc, get(BASE + "/farms/" + farmId + "/cost-entries/totals"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(350.50))
                .andExpect(jsonPath("$.data.byCategory[0].category").value("LABOUR"));
    }

    @Test
    @WithMockUser(authorities = "AGRICULTURE_FINANCE")
    @DisplayName("POST /cost-entries/groups/{id}/reverse works with or without a reason")
    void reverse() throws Exception {
        when(costEntryService.reverseGroup(any(), eq(groupId), any(), any())).thenReturn(List.of(row("-10")));

        TenantRequests.asTenant(mvc, post(BASE + "/cost-entries/groups/" + groupId + "/reverse").with(csrf())).andExpect(status().isOk()).andExpect(jsonPath("$.data[0].amount").value(-10));
        verify(costEntryService).reverseGroup(any(), eq(groupId), any(), isNull());

        TenantRequests.asTenant(mvc, post(BASE + "/cost-entries/groups/" + groupId + "/reverse").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Wrong crop\"}")).andExpect(status().isOk());
        verify(costEntryService).reverseGroup(any(), eq(groupId), any(), eq("Wrong crop"));
    }
}
