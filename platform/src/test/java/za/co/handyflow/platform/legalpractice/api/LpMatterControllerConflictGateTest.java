package za.co.handyflow.platform.legalpractice.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.evidence.application.EvidenceFacade;
import za.co.handyflow.platform.legalpractice.application.internal.*;
import za.co.handyflow.platform.legalpractice.dto.LpMatterResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Opening a matter now requires the conflict check that cleared the client. The service enforces what the check must be; this covers the request shape. */
@WebMvcTest(LpMatterController.class)
@Import(WebMvcTestSecuritySupport.class)
class LpMatterControllerConflictGateTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    @MockitoBean LpMatterService matterService;
    @MockitoBean LpTimeEntryService timeEntryService;
    @MockitoBean LpDisbursementService disbursementService;
    @MockitoBean LpMatterKeyDateService keyDateService;
    @MockitoBean EvidenceFacade evidenceFacade;
    @MockitoBean FeatureGuard featureGuard;

    static final String URL = "/api/v1/legal-practice/matters";

    @BeforeEach
    void seedTenantContext() {
        TenantContext.setTenantId(UUID.randomUUID().toString());
        TenantContext.setUserId(UUID.randomUUID().toString());
        TenantContext.setUserName("Jane Attorney");
    }

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    Map<String, Object> body(boolean withCheck) {
        var b = new java.util.HashMap<String, Object>(Map.of("clientId", UUID.randomUUID().toString(), "attorneyId", UUID.randomUUID().toString(),
                "matterNumber", "M-1", "matterType", "LITIGATION", "matterName", "Dlamini v Nkosi", "billingType", "HOURLY"));
        if (withCheck) b.put("conflictCheckId", UUID.randomUUID().toString());
        return b;
    }

    @Test
    @WithMockUser(authorities = "LEGALPRACTICE_MANAGE")
    @DisplayName("POST matters without a conflictCheckId returns 400 and never reaches the service")
    void openingAMatterWithoutAConflictCheckReturns400() throws Exception {
        mvc.perform(post(URL).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body(false))))
                .andExpect(status().isBadRequest());
        verify(matterService, never()).createMatter(any(), any());
    }

    @Test
    @WithMockUser(authorities = "LEGALPRACTICE_MANAGE")
    @DisplayName("POST matters with a conflictCheckId returns 201")
    void openingAMatterWithAConflictCheckReturns201() throws Exception {
        when(matterService.createMatter(any(), any())).thenReturn(org.mockito.Mockito.mock(LpMatterResponse.class));
        mvc.perform(post(URL).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body(true))))
                .andExpect(status().isCreated());
    }
}
