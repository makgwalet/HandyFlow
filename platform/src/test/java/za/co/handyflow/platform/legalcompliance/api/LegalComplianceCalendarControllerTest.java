package za.co.handyflow.platform.legalcompliance.api;

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
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.legalcompliance.application.internal.LegalComplianceCalendarService;
import za.co.handyflow.platform.legalcompliance.dto.CalendarEntryResponse;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(LegalComplianceCalendarController.class)
@Import(WebMvcTestSecuritySupport.class)
class LegalComplianceCalendarControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    @MockitoBean LegalComplianceCalendarService calendarService;
    @MockitoBean FeatureGuard featureGuard;

    static final String BASE = "/api/v1/legalcompliance/calendar";

    @Test
    @WithMockUser(authorities = "LEGALCOMPLIANCE_READ")
    @DisplayName("GET /calendar returns 200 and includes entries from all three sources")
    void upcomingReturns200WithMixedSources() throws Exception {
        List<CalendarEntryResponse> entries = List.of(
                new CalendarEntryResponse(LocalDate.now().plusDays(5), "OBLIGATION", UUID.randomUUID(),
                        "Annual return", "Review due"),
                new CalendarEntryResponse(LocalDate.now().plusDays(10), "LITIGATION", UUID.randomUUID(),
                        "LM-00001 — Dispute", "Key date"),
                new CalendarEntryResponse(LocalDate.now().plusDays(15), "CONTRACT_RENEWAL", UUID.randomUUID(),
                        "CN-00001 — Supplier NDA", "Auto-renews"));
        when(calendarService.upcoming(any(), anyInt())).thenReturn(entries);

        mvc.perform(get(BASE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(3)))
                .andExpect(jsonPath("$.data[0].sourceType").value("OBLIGATION"))
                .andExpect(jsonPath("$.data[2].sourceType").value("CONTRACT_RENEWAL"));
    }

    @Test
    @WithMockUser(authorities = "SOME_OTHER_AUTHORITY")
    @DisplayName("GET /calendar returns 403 without any LEGALCOMPLIANCE_* authority")
    void upcomingReturns403WithoutAuthority() throws Exception {
        mvc.perform(get(BASE)).andExpect(status().isForbidden());
    }
}
