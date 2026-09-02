package za.co.handyflow.platform.legalcompliance.api;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import za.co.handyflow.platform.legalcompliance.application.internal.LegalCompliancePdfService;
import za.co.handyflow.platform.legalcompliance.application.internal.PopiaProcessingActivityService;
import za.co.handyflow.platform.legalcompliance.domain.model.DataCategory;
import za.co.handyflow.platform.legalcompliance.domain.model.LawfulBasis;
import za.co.handyflow.platform.legalcompliance.domain.model.PopiaProcessingActivity;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PopiaProcessingActivityController.class)
@Import(WebMvcTestSecuritySupport.class)
class PopiaProcessingActivityControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    @MockitoBean PopiaProcessingActivityService activityService;
    @MockitoBean LegalCompliancePdfService pdfService;
    @MockitoBean FeatureGuard featureGuard;

    static final String BASE = "/api/v1/legalcompliance/popia-activities";

    private PopiaProcessingActivity activity(TenantId tenantId) {
        return PopiaProcessingActivity.create(tenantId, "Payroll processing", DataCategory.EMPLOYEE,
                "Pay employees", LawfulBasis.CONTRACT, "HR", null, null, "7 years", false, null,
                "Encrypted", null, UUID.randomUUID());
    }

    @Test
    @WithMockUser(authorities = "LEGALCOMPLIANCE_READ")
    @DisplayName("GET /popia-activities returns 200 with LEGALCOMPLIANCE_READ")
    void listReturns200() throws Exception {
        TenantId tenantId = TenantId.generate();
        when(activityService.list(any())).thenReturn(List.of(activity(tenantId)));

        mvc.perform(get(BASE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].activityName").value("Payroll processing"));
    }

    @Test
    @WithMockUser(authorities = "SOME_OTHER_AUTHORITY")
    @DisplayName("GET /popia-activities returns 403 without any LEGALCOMPLIANCE_* authority")
    void listReturns403WithoutAuthority() throws Exception {
        mvc.perform(get(BASE)).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "LEGALCOMPLIANCE_MANAGE")
    @DisplayName("POST /popia-activities missing required lawfulBasis fails validation (400)")
    void createMissingLawfulBasisReturns400() throws Exception {
        mvc.perform(post(BASE).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"activityName":"Payroll processing","dataCategory":"EMPLOYEE"}
                            """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(authorities = "LEGALCOMPLIANCE_MANAGE")
    @DisplayName("POST /popia-activities with valid payload returns 201")
    void createReturns201() throws Exception {
        TenantId tenantId = TenantId.generate();
        when(activityService.create(any(), anyString(), any(), any(), any(), any(), any(), any(), any(),
                anyBoolean(), any(), any(), any(), any())).thenReturn(activity(tenantId));

        mvc.perform(post(BASE).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"activityName":"Payroll processing","dataCategory":"EMPLOYEE","lawfulBasis":"CONTRACT"}
                            """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.activityName").value("Payroll processing"));
    }

    @Test
    @WithMockUser(authorities = "LEGALCOMPLIANCE_ADMIN")
    @DisplayName("DELETE /popia-activities/{id} with LEGALCOMPLIANCE_ADMIN returns 200")
    void deleteWithAdminReturns200() throws Exception {
        mvc.perform(delete(BASE + "/" + UUID.randomUUID()).with(csrf()))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = "LEGALCOMPLIANCE_READ")
    @DisplayName("DELETE /popia-activities/{id} with only LEGALCOMPLIANCE_READ returns 403")
    void deleteWithReadOnlyReturns403() throws Exception {
        mvc.perform(delete(BASE + "/" + UUID.randomUUID()).with(csrf()))
                .andExpect(status().isForbidden());
    }
}
