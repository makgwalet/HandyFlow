package za.co.handyflow.platform.agriculture.api;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import za.co.handyflow.platform.WebMvcTestSecuritySupport;
import za.co.handyflow.platform.agriculture.application.internal.AgProfitabilityService;
import za.co.handyflow.platform.agriculture.dto.ProfitabilityDtos.FarmMargin;
import za.co.handyflow.platform.agriculture.dto.ProfitabilityDtos.ProfitabilityOverviewResponse;
import za.co.handyflow.platform.agriculture.dto.ProfitabilityDtos.ProfitabilityResponse;
import za.co.handyflow.platform.agriculture.dto.ProfitabilityDtos.Subtotal;
import za.co.handyflow.platform.agriculture.dto.ProfitabilityDtos.Totals;
import za.co.handyflow.platform.billing.FeatureGuard;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AgProfitabilityController.class)
@Import(WebMvcTestSecuritySupport.class)
class AgProfitabilityControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean AgProfitabilityService profitabilityService;
    @MockitoBean FeatureGuard featureGuard;

    final UUID farmId = UUID.randomUUID();

    @AfterEach
    void clearTenantContext() { TenantRequests.clear(); }

    private static final BigDecimal Z = BigDecimal.ZERO;

    private ProfitabilityResponse response() {
        return new ProfitabilityResponse(farmId, new Totals(new BigDecimal("5000.00"), Z, Z, Z, Z, Z, new BigDecimal("1500.00"), new BigDecimal("3500.00"), new BigDecimal("70.0")),
                new Subtotal(1, new BigDecimal("5000.00"), new BigDecimal("1500.00"), new BigDecimal("3500.00"), new BigDecimal("70.0")), new Subtotal(0, Z, Z, Z, null), new Subtotal(0, Z, Z, Z, null), List.of(), List.of("Gross margin only."));
    }

    @Test
    @WithMockUser(authorities = {"AGRICULTURE_FINANCE", "INVOICE_READ"})
    @DisplayName("the report is returned, and both modules are checked")
    void returnsReport() throws Exception {
        when(profitabilityService.farm(any(), eq(farmId), any())).thenReturn(response());

        TenantRequests.asTenant(mvc, get("/api/v1/agriculture/farms/" + farmId + "/profitability"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totals.grossMargin").value(3500.00))
                .andExpect(jsonPath("$.data.complete.units").value(1));

        verify(featureGuard, atLeastOnce()).requireModule("agriculture");
        verify(featureGuard, atLeastOnce()).requireModule("invoicing");
    }

    @Test
    @WithMockUser(authorities = {"AGRICULTURE_FINANCE", "INVOICE_READ"})
    @DisplayName("an optional seasonId is passed through, and without one the whole farm is asked for")
    void passesSeasonThrough() throws Exception {
        UUID seasonId = UUID.randomUUID();
        when(profitabilityService.farm(any(), eq(farmId), eq(seasonId))).thenReturn(response());
        when(profitabilityService.farm(any(), eq(farmId), isNull())).thenReturn(response());

        TenantRequests.asTenant(mvc, get("/api/v1/agriculture/farms/" + farmId + "/profitability").param("seasonId", seasonId.toString())).andExpect(status().isOk());
        TenantRequests.asTenant(mvc, get("/api/v1/agriculture/farms/" + farmId + "/profitability")).andExpect(status().isOk());

        verify(profitabilityService).farm(any(), eq(farmId), eq(seasonId));
        verify(profitabilityService).farm(any(), eq(farmId), isNull());
    }

    @Test
    @WithMockUser(authorities = "AGRICULTURE_FINANCE")
    @DisplayName("finance alone is not enough: the report shows revenue, so it needs INVOICE_READ as well")
    void needsInvoiceRead() throws Exception {
        TenantRequests.asTenant(mvc, get("/api/v1/agriculture/farms/" + farmId + "/profitability")).andExpect(status().isForbidden());
        verifyNoInteractions(profitabilityService);
    }

    @Test
    @WithMockUser(authorities = "INVOICE_READ")
    @DisplayName("INVOICE_READ alone is not enough either")
    void needsFinance() throws Exception {
        TenantRequests.asTenant(mvc, get("/api/v1/agriculture/farms/" + farmId + "/profitability")).andExpect(status().isForbidden());
        verifyNoInteractions(profitabilityService);
    }

    @Test
    @WithMockUser(authorities = {"AGRICULTURE_READ", "AGRICULTURE_MANAGE", "AGRICULTURE_ADMIN", "FLEET_READ", "FUEL_MARGIN_READ"})
    @DisplayName("broad Agriculture rights and other modules' cost rights do not open the margins")
    void broadRightsAreNotEnough() throws Exception {
        TenantRequests.asTenant(mvc, get("/api/v1/agriculture/farms/" + farmId + "/profitability")).andExpect(status().isForbidden());
        verifyNoInteractions(profitabilityService);
    }

    // ---- all-farms overview --------------------------------------------------------------------------------------------

    private ProfitabilityOverviewResponse overviewResponse() {
        Subtotal none = new Subtotal(0, Z, Z, Z, null);
        return new ProfitabilityOverviewResponse(new Totals(new BigDecimal("5000.00"), Z, Z, Z, Z, Z, new BigDecimal("1500.00"), new BigDecimal("3500.00"), new BigDecimal("70.0")), none, none, none,
                List.of(new FarmMargin(farmId, "Home Farm", new BigDecimal("5000.00"), new BigDecimal("1500.00"), new BigDecimal("3500.00"), new BigDecimal("70.0"), 1, 0, 0, 0)), List.of("Gross margin only."));
    }

    @Test
    @WithMockUser(authorities = {"AGRICULTURE_FINANCE", "INVOICE_READ"})
    @DisplayName("the overview is returned, and both modules are checked")
    void returnsOverview() throws Exception {
        when(profitabilityService.overview(any())).thenReturn(overviewResponse());

        TenantRequests.asTenant(mvc, get("/api/v1/agriculture/profitability/overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totals.grossMargin").value(3500.00))
                .andExpect(jsonPath("$.data.farms[0].farmName").value("Home Farm"));

        verify(featureGuard, atLeastOnce()).requireModule("agriculture");
        verify(featureGuard, atLeastOnce()).requireModule("invoicing");
    }

    @Test
    @WithMockUser(authorities = "AGRICULTURE_FINANCE")
    @DisplayName("the overview needs INVOICE_READ as well: it shows revenue for every farm")
    void overviewNeedsInvoiceRead() throws Exception {
        TenantRequests.asTenant(mvc, get("/api/v1/agriculture/profitability/overview")).andExpect(status().isForbidden());
        verify(profitabilityService, never()).overview(any());
    }

    @Test
    @WithMockUser(authorities = "INVOICE_READ")
    @DisplayName("INVOICE_READ alone does not open the overview either")
    void overviewNeedsFinance() throws Exception {
        TenantRequests.asTenant(mvc, get("/api/v1/agriculture/profitability/overview")).andExpect(status().isForbidden());
        verify(profitabilityService, never()).overview(any());
    }

    @Test
    @WithMockUser(authorities = {"AGRICULTURE_READ", "AGRICULTURE_MANAGE", "AGRICULTURE_ADMIN", "FLEET_READ", "FUEL_MARGIN_READ"})
    @DisplayName("broad Agriculture rights do not open every farm's margins")
    void overviewBroadRightsAreNotEnough() throws Exception {
        TenantRequests.asTenant(mvc, get("/api/v1/agriculture/profitability/overview")).andExpect(status().isForbidden());
        verify(profitabilityService, never()).overview(any());
    }
}
