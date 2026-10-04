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
import za.co.handyflow.platform.agriculture.dto.ProfitabilityDtos.ProfitabilityResponse;
import za.co.handyflow.platform.agriculture.dto.ProfitabilityDtos.Subtotal;
import za.co.handyflow.platform.agriculture.dto.ProfitabilityDtos.Totals;
import za.co.handyflow.platform.billing.FeatureGuard;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
        when(profitabilityService.farm(any(), eq(farmId))).thenReturn(response());

        TenantRequests.asTenant(mvc, get("/api/v1/agriculture/farms/" + farmId + "/profitability"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totals.grossMargin").value(3500.00))
                .andExpect(jsonPath("$.data.complete.units").value(1));

        verify(featureGuard, atLeastOnce()).requireModule("agriculture");
        verify(featureGuard, atLeastOnce()).requireModule("invoicing");
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
}
