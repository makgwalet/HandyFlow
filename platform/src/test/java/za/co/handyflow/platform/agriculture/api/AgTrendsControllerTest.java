package za.co.handyflow.platform.agriculture.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import za.co.handyflow.platform.WebMvcTestSecuritySupport;
import za.co.handyflow.platform.agriculture.application.internal.AgTrendsService;
import za.co.handyflow.platform.agriculture.dto.AgTrendsResponse;
import za.co.handyflow.platform.billing.FeatureGuard;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AgTrendsController.class)
@Import(WebMvcTestSecuritySupport.class)
class AgTrendsControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean AgTrendsService trendsService;
    @MockitoBean FeatureGuard featureGuard;

    static final String URL = "/api/v1/agriculture/trends";

    private AgTrendsResponse response() {
        LocalDate d = LocalDate.of(2026, 10, 1);
        return new AgTrendsResponse(d, null, List.of(new AgTrendsResponse.Month("2026-10", d, d, true)), List.of(),
                new AgTrendsResponse.Production(List.of(), List.of(), 0), List.of(), List.of(), List.of("Herd size over time is not recorded."));
    }

    @Test
    @WithMockUser(authorities = "AGRICULTURE_READ")
    @DisplayName("GET /trends defaults to all farms and 12 months, and checks the module is enabled")
    void defaults() throws Exception {
        when(trendsService.getTrends(any(), any(), anyInt())).thenReturn(response());

        mvc.perform(get(URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.months[0].key").value("2026-10"))
                .andExpect(jsonPath("$.data.limitations[0]").value("Herd size over time is not recorded."));

        verify(trendsService).getTrends(any(), isNull(), eq(12));
        verify(featureGuard, atLeastOnce()).requireModule("agriculture");
    }

    @Test
    @WithMockUser(authorities = "AGRICULTURE_READ")
    @DisplayName("GET /trends passes the farm and the number of months through")
    void passesParameters() throws Exception {
        UUID farm = UUID.randomUUID();
        when(trendsService.getTrends(any(), any(), anyInt())).thenReturn(response());

        mvc.perform(get(URL).param("farmId", farm.toString()).param("months", "6")).andExpect(status().isOk());

        verify(trendsService).getTrends(any(), eq(farm), eq(6));
    }

    @Test
    @WithMockUser(authorities = "AGRICULTURE_MANAGE")
    @DisplayName("GET /trends without AGRICULTURE_READ is forbidden")
    void requiresRead() throws Exception {
        mvc.perform(get(URL)).andExpect(status().isForbidden());
        verifyNoInteractions(trendsService);
    }
}
