package za.co.handyflow.platform.facilities.api;

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
import za.co.handyflow.platform.facilities.application.internal.FacilityComplianceCertificateService;
import za.co.handyflow.platform.facilities.dto.ComplianceCertificateResponse;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Revocation is ADMIN-only — this module's own equivalent of every other
 * module's issue/revoke gating on a certificate-shaped entity.
 */
@WebMvcTest(FacilityComplianceCertificateController.class)
@Import(WebMvcTestSecuritySupport.class)
class FacilityComplianceCertificateControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    @MockitoBean FacilityComplianceCertificateService certificateService;
    @MockitoBean FeatureGuard featureGuard;

    static final String BASE = "/api/v1/facilities/compliance";

    private ComplianceCertificateResponse testResponse() {
        return new ComplianceCertificateResponse(UUID.randomUUID(), UUID.randomUUID(), null,
                "ELECTRICAL_COC", "COC-001", "ABC Electrical", LocalDate.now(), LocalDate.now().plusYears(1),
                null, "VALID", null, Instant.now());
    }

    @Test
    @WithMockUser(authorities = "FACILITIES_MANAGE")
    @DisplayName("POST /{id}/revoke with only FACILITIES_MANAGE returns 403 — ADMIN only")
    void revokeWithManageOnlyReturns403() throws Exception {
        mvc.perform(post(BASE + "/" + UUID.randomUUID() + "/revoke").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"reason":"Issued in error"}
                            """))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "FACILITIES_ADMIN")
    @DisplayName("POST /{id}/revoke with FACILITIES_ADMIN returns 200")
    void revokeWithAdminReturns200() throws Exception {
        ComplianceCertificateResponse resp = testResponse();
        when(certificateService.revoke(any(), any(), any())).thenReturn(resp);

        mvc.perform(post(BASE + "/" + resp.id() + "/revoke").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"reason":"Issued in error"}
                            """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.certificateType").value("ELECTRICAL_COC"));
    }

    @Test
    @WithMockUser(authorities = "FACILITIES_READ")
    @DisplayName("POST /{id}/revoke with only FACILITIES_READ returns 403")
    void revokeWithReadOnlyReturns403() throws Exception {
        mvc.perform(post(BASE + "/" + UUID.randomUUID() + "/revoke").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"reason":"test"}
                            """))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "SOME_OTHER_AUTHORITY")
    @DisplayName("GET /compliance returns 403 without any FACILITIES_* authority")
    void getReturns403WithoutAuthority() throws Exception {
        mvc.perform(get(BASE)).andExpect(status().isForbidden());
    }
}
