package za.co.handyflow.platform.legalcompliance.api;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.evidence.application.EvidenceFacade;
import za.co.handyflow.platform.legalcompliance.application.internal.DsarRequestService;
import za.co.handyflow.platform.legalcompliance.application.internal.LegalCompliancePdfService;
import za.co.handyflow.platform.legalcompliance.domain.model.DataCategory;
import za.co.handyflow.platform.legalcompliance.domain.model.DsarRequest;
import za.co.handyflow.platform.legalcompliance.domain.model.DsarRequestType;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(DsarRequestController.class)
@Import(WebMvcTestSecuritySupport.class)
class DsarRequestControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    @MockitoBean DsarRequestService dsarService;
    @MockitoBean EvidenceFacade evidenceFacade;
    @MockitoBean LegalCompliancePdfService pdfService;
    @MockitoBean FeatureGuard featureGuard;

    static final String BASE = "/api/v1/legalcompliance/dsar-requests";

    private DsarRequest request(TenantId tenantId) {
        return DsarRequest.create(tenantId, "DSAR-00001", DsarRequestType.ACCESS, DataCategory.CUSTOMER,
                "Jane Requester", "jane@example.com", null, LocalDate.now(), UUID.randomUUID());
    }

    @Test
    @WithMockUser(authorities = "LEGALCOMPLIANCE_READ")
    @DisplayName("GET /dsar-requests returns 200 with LEGALCOMPLIANCE_READ")
    void listReturns200() throws Exception {
        TenantId tenantId = TenantId.generate();
        when(dsarService.list(any(), any(), any())).thenReturn(new PageImpl<>(List.of(request(tenantId))));

        mvc.perform(get(BASE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.content[0].requestNumber").value("DSAR-00001"));
    }

    @Test
    @WithMockUser(authorities = "LEGALCOMPLIANCE_READ")
    @DisplayName("POST /dsar-requests with only LEGALCOMPLIANCE_READ returns 403")
    void createWithReadOnlyReturns403() throws Exception {
        mvc.perform(post(BASE).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"requestType":"ACCESS","dataCategory":"CUSTOMER","requesterName":"Jane",
                             "receivedDate":"2026-01-01"}
                            """))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "LEGALCOMPLIANCE_MANAGE")
    @DisplayName("POST /dsar-requests with LEGALCOMPLIANCE_MANAGE returns 201")
    void createReturns201() throws Exception {
        TenantId tenantId = TenantId.generate();
        when(dsarService.create(any(), any(), any(), anyString(), any(), any(), any(), any()))
                .thenReturn(request(tenantId));

        mvc.perform(post(BASE).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"requestType":"ACCESS","dataCategory":"CUSTOMER","requesterName":"Jane",
                             "receivedDate":"2026-01-01"}
                            """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.requestNumber").value("DSAR-00001"));
    }

    @Test
    @WithMockUser(authorities = "LEGALCOMPLIANCE_MANAGE")
    @DisplayName("POST /dsar-requests/{id}/complete returns 200")
    void completeReturns200() throws Exception {
        TenantId tenantId = TenantId.generate();
        DsarRequest r = request(tenantId);
        r.complete("done");
        when(dsarService.complete(any(), any(), any())).thenReturn(r);

        mvc.perform(post(BASE + "/" + UUID.randomUUID() + "/complete").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resolutionNotes\":\"Export sent\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"));
    }

    @Test
    @WithMockUser(authorities = "LEGALCOMPLIANCE_ADMIN")
    @DisplayName("DELETE /dsar-requests/{id} with LEGALCOMPLIANCE_ADMIN returns 200")
    void deleteWithAdminReturns200() throws Exception {
        mvc.perform(delete(BASE + "/" + UUID.randomUUID()).with(csrf()))
                .andExpect(status().isOk());
    }
}
