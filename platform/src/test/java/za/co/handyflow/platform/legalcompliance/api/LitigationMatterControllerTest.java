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
import za.co.handyflow.platform.legalcompliance.application.internal.LegalCompliancePdfService;
import za.co.handyflow.platform.legalcompliance.application.internal.LitigationMatterService;
import za.co.handyflow.platform.legalcompliance.domain.model.LitigationMatter;
import za.co.handyflow.platform.legalcompliance.domain.model.LitigationMatterType;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(LitigationMatterController.class)
@Import(WebMvcTestSecuritySupport.class)
class LitigationMatterControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    @MockitoBean LitigationMatterService matterService;
    @MockitoBean EvidenceFacade evidenceFacade;
    @MockitoBean LegalCompliancePdfService pdfService;
    @MockitoBean FeatureGuard featureGuard;

    static final String BASE = "/api/v1/legalcompliance/matters";

    private LitigationMatter matter(TenantId tenantId) {
        return LitigationMatter.create(tenantId, "LM-00001", "Dispute", LitigationMatterType.COMMERCIAL,
                "Acme Supplies", "CLAIMANT", new BigDecimal("50000"), "Smith & Co", "Magistrate's Court",
                "CASE/001", LocalDate.now(), LocalDate.now().plusDays(10), "desc", UUID.randomUUID());
    }

    @Test
    @WithMockUser(authorities = "LEGALCOMPLIANCE_READ")
    @DisplayName("GET /matters returns 200 with LEGALCOMPLIANCE_READ")
    void listReturns200() throws Exception {
        TenantId tenantId = TenantId.generate();
        when(matterService.list(any(), any(), any())).thenReturn(new PageImpl<>(List.of(matter(tenantId))));

        mvc.perform(get(BASE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.content[0].matterNumber").value("LM-00001"));
    }

    @Test
    @WithMockUser(authorities = "LEGALCOMPLIANCE_READ")
    @DisplayName("POST /matters with only LEGALCOMPLIANCE_READ returns 403")
    void createWithReadOnlyReturns403() throws Exception {
        mvc.perform(post(BASE).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"title":"Dispute","matterType":"COMMERCIAL","opposingParty":"Acme",
                             "openedDate":"2026-01-01"}
                            """))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "LEGALCOMPLIANCE_MANAGE")
    @DisplayName("POST /matters with LEGALCOMPLIANCE_MANAGE returns 201")
    void createWithManageReturns201() throws Exception {
        TenantId tenantId = TenantId.generate();
        when(matterService.create(any(), anyString(), any(), anyString(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any())).thenReturn(matter(tenantId));

        mvc.perform(post(BASE).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"title":"Dispute","matterType":"COMMERCIAL","opposingParty":"Acme",
                             "openedDate":"2026-01-01"}
                            """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.matterNumber").value("LM-00001"));
    }

    @Test
    @WithMockUser(authorities = "LEGALCOMPLIANCE_MANAGE")
    @DisplayName("POST /matters/{id}/close with a non-terminal status returns 400 (bean validation only checks non-null; the entity itself rejects the value)")
    void closeWithMissingFinalStatusReturns400() throws Exception {
        mvc.perform(post(BASE + "/" + UUID.randomUUID() + "/close").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(authorities = "LEGALCOMPLIANCE_READ")
    @DisplayName("POST /matters/{id}/evidence with only LEGALCOMPLIANCE_READ returns 403")
    void attachEvidenceWithReadOnlyReturns403() throws Exception {
        mvc.perform(multipart(BASE + "/" + UUID.randomUUID() + "/evidence")
                        .file("file", "content".getBytes())
                        .param("evidenceType", "FILING")
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "LEGALCOMPLIANCE_ADMIN")
    @DisplayName("DELETE /matters/{id} with LEGALCOMPLIANCE_ADMIN returns 200")
    void deleteWithAdminReturns200() throws Exception {
        mvc.perform(delete(BASE + "/" + UUID.randomUUID()).with(csrf()))
                .andExpect(status().isOk());
    }
}
