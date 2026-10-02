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
import za.co.handyflow.platform.agriculture.application.internal.AgDashboardService;
import za.co.handyflow.platform.agriculture.dto.AgDashboardResponse;
import za.co.handyflow.platform.billing.FeatureGuard;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AgDashboardController.class)
@Import(WebMvcTestSecuritySupport.class)
class AgDashboardControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean AgDashboardService dashboardService;
    @MockitoBean FeatureGuard featureGuard;

    static final String URL = "/api/v1/agriculture/dashboard";

    // The handlers read the tenant from TenantContext, which the JWT filter normally fills and then clears after every request. This slice has
    // no real login, so TenantRequests seeds it right before each request; clearing it here keeps it from leaking into other test classes.
    @AfterEach
    void clearTenantContext() {
        TenantRequests.clear();
    }

    private AgDashboardResponse emptyDashboard() {
        return new AgDashboardResponse(LocalDate.of(2026, 10, 1),
                new AgDashboardResponse.Totals(2, new BigDecimal("500"), 0, 3L, new BigDecimal("228.5"), 1L, 50L, 4L, 185L, 235L),
                List.of(), List.of(), new AgDashboardResponse.CropSummary(List.of(), List.of()), List.of(),
                new AgDashboardResponse.AttentionSummary(0, List.of(), List.of()));
    }

    @Test
    @WithMockUser(authorities = "AGRICULTURE_READ")
    @DisplayName("GET /dashboard returns the totals and checks the agriculture module is enabled")
    void returnsDashboard() throws Exception {
        when(dashboardService.getTenantDashboard(any())).thenReturn(emptyDashboard());

        TenantRequests.asTenant(mvc, get(URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totals.farmCount").value(2))
                .andExpect(jsonPath("$.data.totals.totalHead").value(235))
                .andExpect(jsonPath("$.data.attention.total").value(0));

        verify(featureGuard, atLeastOnce()).requireModule("agriculture");
    }

    @Test
    @WithMockUser(authorities = "AGRICULTURE_MANAGE")
    @DisplayName("GET /dashboard without AGRICULTURE_READ is forbidden")
    void requiresRead() throws Exception {
        TenantRequests.asTenant(mvc, get(URL)).andExpect(status().isForbidden());
        verifyNoInteractions(dashboardService);
    }
}
