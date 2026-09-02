package za.co.handyflow.platform.training.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import za.co.handyflow.platform.WebMvcTestSecuritySupport;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.training.application.internal.TrainingCertificateService;
import za.co.handyflow.platform.training.application.internal.TrainingPdfService;
import za.co.handyflow.platform.training.domain.model.TrainingCertificate;

import java.time.LocalDate;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Issuing/revoking a certificate is this module's own financial-commit-
 * point equivalent — ADMIN-only, same convention
 * CollAgencyTrustControllerTest/WhseBillingControllerTest already
 * establish for irreversible/formally-consequential actions in this
 * codebase.
 */
@WebMvcTest(TrainingCertificateController.class)
@Import(WebMvcTestSecuritySupport.class)
class TrainingCertificateControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    @MockitoBean TrainingCertificateService certificateService;
    @MockitoBean TrainingPdfService pdfService;
    @MockitoBean JdbcTemplate jdbc;
    @MockitoBean FeatureGuard featureGuard;

    static final String BASE = "/api/v1/training";

    private TrainingCertificate testCertificate() {
        return TrainingCertificate.create(TenantId.generate(), UUID.randomUUID(), UUID.randomUUID(),
                "Jane Dlamini", "First Aid Level 1", "CERT-00001", LocalDate.now(), LocalDate.now().plusYears(1));
    }

    @Test
    @WithMockUser(authorities = "TRAINING_MANAGE")
    @DisplayName("POST .../certificate with only TRAINING_MANAGE returns 403 — ADMIN only")
    void issueCertificateWithManageOnlyReturns403() throws Exception {
        mvc.perform(post(BASE + "/enrollments/" + UUID.randomUUID() + "/certificate").with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "TRAINING_ADMIN")
    @DisplayName("POST .../certificate with TRAINING_ADMIN returns 201")
    void issueCertificateWithAdminReturns201() throws Exception {
        when(certificateService.issue(any(), any())).thenReturn(testCertificate());

        mvc.perform(post(BASE + "/enrollments/" + UUID.randomUUID() + "/certificate").with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.certificateNumber").value("CERT-00001"))
                .andExpect(jsonPath("$.data.status").value("VALID"));
    }

    @Test
    @WithMockUser(authorities = "TRAINING_MANAGE")
    @DisplayName("POST .../revoke with only TRAINING_MANAGE returns 403 — ADMIN only")
    void revokeCertificateWithManageOnlyReturns403() throws Exception {
        mvc.perform(post(BASE + "/certificates/" + UUID.randomUUID() + "/revoke").with(csrf())
                        .contentType("application/json").content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "TRAINING_READ")
    @DisplayName("GET /certificates/{id} returns 200 with TRAINING_READ")
    void getCertificateReturns200WithRead() throws Exception {
        when(certificateService.get(any(), any())).thenReturn(testCertificate());

        mvc.perform(get(BASE + "/certificates/" + UUID.randomUUID()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.employeeNameSnapshot").value("Jane Dlamini"));
    }

    @Test
    @WithMockUser(authorities = "SOME_OTHER_AUTHORITY")
    @DisplayName("GET /certificates returns 403 without any TRAINING_* authority")
    void listReturns403WithoutAuthority() throws Exception {
        mvc.perform(get(BASE + "/certificates")).andExpect(status().isForbidden());
    }
}
