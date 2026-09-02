package za.co.handyflow.platform.legalcompliance.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import za.co.handyflow.platform.WebMvcTestSecuritySupport;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.legalcompliance.application.internal.LegalCompliancePdfService;
import za.co.handyflow.platform.legalcompliance.application.internal.RegulatoryObligationService;
import za.co.handyflow.platform.legalcompliance.domain.model.ObligationCategory;
import za.co.handyflow.platform.legalcompliance.domain.model.RecurrenceInterval;
import za.co.handyflow.platform.legalcompliance.domain.model.RegulatoryObligation;
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

/**
 * Spring MVC slice test — loads only the web layer, no DB. Confirms the
 * LEGALCOMPLIANCE_READ/_MANAGE/_ADMIN @PreAuthorize gates actually fire,
 * same intent as HrControllerTest's own authority-gate regression tests.
 * FeatureGuard is mocked (its requireModule() is void and a no-op mock
 * never throws) rather than left unmocked, since the real bean needs
 * BillingFacade + SubscriptionRepository, which aren't part of this slice.
 */
@WebMvcTest(RegulatoryObligationController.class)
@Import(WebMvcTestSecuritySupport.class)
class RegulatoryObligationControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    @MockitoBean RegulatoryObligationService obligationService;
    @MockitoBean LegalCompliancePdfService pdfService;
    @MockitoBean FeatureGuard featureGuard;

    static final String BASE = "/api/v1/legalcompliance/obligations";

    private RegulatoryObligation obligation(TenantId tenantId) {
        RegulatoryObligation o = RegulatoryObligation.create(tenantId, "Annual return", ObligationCategory.COMPANIES_ACT,
                "Companies Act", "File return", null, null, LocalDate.now().plusDays(10),
                RecurrenceInterval.ANNUALLY, UUID.randomUUID());
        return o;
    }

    @Test
    @WithMockUser(authorities = "LEGALCOMPLIANCE_READ")
    @DisplayName("GET /obligations returns 200 with LEGALCOMPLIANCE_READ")
    void listReturns200WithReadAuthority() throws Exception {
        TenantId tenantId = TenantId.generate();
        Page<RegulatoryObligation> page = new PageImpl<>(List.of(obligation(tenantId)));
        when(obligationService.list(any(), any(), any())).thenReturn(page);

        mvc.perform(get(BASE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(1)));
    }

    @Test
    @WithMockUser(authorities = "SOME_OTHER_AUTHORITY")
    @DisplayName("GET /obligations returns 403 without any LEGALCOMPLIANCE_* authority")
    void listReturns403WithoutAuthority() throws Exception {
        mvc.perform(get(BASE)).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "LEGALCOMPLIANCE_READ")
    @DisplayName("POST /obligations with only LEGALCOMPLIANCE_READ returns 403")
    void createWithReadOnlyReturns403() throws Exception {
        mvc.perform(post(BASE).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"title":"Annual return","category":"COMPANIES_ACT",
                             "reviewDate":"2027-01-01","recurrence":"ANNUALLY"}
                            """))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "LEGALCOMPLIANCE_MANAGE")
    @DisplayName("POST /obligations with LEGALCOMPLIANCE_MANAGE returns 201")
    void createWithManageReturns201() throws Exception {
        TenantId tenantId = TenantId.generate();
        when(obligationService.create(any(), anyString(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(obligation(tenantId));

        mvc.perform(post(BASE).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"title":"Annual return","category":"COMPANIES_ACT",
                             "reviewDate":"2027-01-01","recurrence":"ANNUALLY"}
                            """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.title").value("Annual return"));
    }

    @Test
    @WithMockUser(authorities = "LEGALCOMPLIANCE_MANAGE")
    @DisplayName("DELETE /obligations/{id} with only LEGALCOMPLIANCE_MANAGE returns 403 — delete requires ADMIN")
    void deleteWithManageOnlyReturns403() throws Exception {
        mvc.perform(delete(BASE + "/" + UUID.randomUUID()).with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "LEGALCOMPLIANCE_ADMIN")
    @DisplayName("DELETE /obligations/{id} with LEGALCOMPLIANCE_ADMIN returns 200")
    void deleteWithAdminReturns200() throws Exception {
        UUID id = UUID.randomUUID();
        mvc.perform(delete(BASE + "/" + id).with(csrf()))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = "LEGALCOMPLIANCE_MANAGE")
    @DisplayName("POST /obligations/{id}/mark-non-compliant with blank notes fails validation (400)")
    void markNonCompliantRequiresNotes() throws Exception {
        mvc.perform(post(BASE + "/" + UUID.randomUUID() + "/mark-non-compliant").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"notes\":\"\"}"))
                .andExpect(status().isBadRequest());
    }
}
