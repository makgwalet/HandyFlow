package za.co.handyflow.platform.compliancetender.api;

import org.junit.jupiter.api.AfterEach;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.ResultActions;
import za.co.handyflow.platform.shared.TenantContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import za.co.handyflow.platform.WebMvcTestSecuritySupport;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.businessreadiness.ReadinessAssessment;
import za.co.handyflow.platform.businessreadiness.ReadinessItem;
import za.co.handyflow.platform.businessreadiness.ReadinessResult;
import za.co.handyflow.platform.businessreadiness.ReadinessSummary;
import za.co.handyflow.platform.compliancetender.application.internal.TenderPdfService;
import za.co.handyflow.platform.compliancetender.application.internal.TenderPersonnelService;
import za.co.handyflow.platform.compliancetender.application.internal.TenderPricingService;
import za.co.handyflow.platform.compliancetender.application.internal.TenderReadinessService;
import za.co.handyflow.platform.compliancetender.application.internal.TenderService;
import za.co.handyflow.platform.compliancetender.application.internal.TenderSnapshotService;
import za.co.handyflow.platform.shared.ResourceNotFoundException;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Access rules and shape of GET /tenders/{id}/readiness (business readiness, ADR-003). The judging itself is tested in RequirementReadinessEvaluatorTest. */
@WebMvcTest(TenderController.class)
@Import(WebMvcTestSecuritySupport.class)
class TenderControllerReadinessTest {

    @Autowired MockMvc mvc;

    /** Performs a request as a tenant user. The tenant is seeded before EACH request because JwtAuthFilter clears TenantContext when a request finishes (same reason as the agriculture tests' TenantRequests). */
    private ResultActions asTenant(RequestBuilder request) throws Exception {
        TenantContext.setTenantId(UUID.randomUUID().toString());
        TenantContext.setUserId(UUID.randomUUID().toString());
        return mvc.perform(request);
    }

    @AfterEach
    void clearTenant() { TenantContext.clear(); }


    @MockitoBean TenderService tenderService;
    @MockitoBean TenderPersonnelService personnelService;
    @MockitoBean TenderSnapshotService snapshotService;
    @MockitoBean TenderPdfService pdfService;
    @MockitoBean TenderReadinessService readinessService;
    @MockitoBean TenderPricingService pricingService;
    @MockitoBean FeatureGuard featureGuard;

    final UUID tenderId = UUID.randomUUID();
    final String url = "/api/v1/compliance/tenders/" + tenderId + "/readiness";

    private ReadinessAssessment assessment() {
        ReadinessItem item = new ReadinessItem(UUID.randomUUID(), "Valid CSD registration", "MET", ReadinessResult.MISSING, "No CSD registration is recorded", null, false, true, false);
        return new ReadinessAssessment(LocalDate.of(2026, 11, 15), "CLOSING_DATE", List.of(item), new ReadinessSummary(1, 0, 1, 0, 0, 0, 0, 0, 1));
    }

    @Test
    @WithMockUser(authorities = "COMPLIANCE_READ")
    @DisplayName("COMPLIANCE_READ gets the assessment: the as-of date and its basis, each item, and the counts; the module is checked")
    void readReturnsTheAssessment() throws Exception {
        when(readinessService.assess(any(), eq(tenderId))).thenReturn(assessment());

        asTenant(get(url))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.asOf").value("2026-11-15"))
                .andExpect(jsonPath("$.data.asOfBasis").value("CLOSING_DATE"))
                .andExpect(jsonPath("$.data.items", hasSize(1)))
                .andExpect(jsonPath("$.data.items[0].result").value("MISSING"))
                .andExpect(jsonPath("$.data.items[0].differsFromManualStatus").value(true))
                .andExpect(jsonPath("$.data.summary.missing").value(1));

        verify(featureGuard, atLeastOnce()).requireModule("compliancetender");
    }

    @Test
    @DisplayName("COMPLIANCE_MANAGE and COMPLIANCE_ADMIN can read it too")
    void manageAndAdminCanRead() throws Exception {
        when(readinessService.assess(any(), eq(tenderId))).thenReturn(assessment());

        for (String authority : new String[] {"COMPLIANCE_MANAGE", "COMPLIANCE_ADMIN"}) {
            asTenant(get(url).with(user("u").authorities(new SimpleGrantedAuthority(authority)))).andExpect(status().isOk());
        }
    }

    @Test
    @WithMockUser(authorities = {"COMPLIANCE_SERVICES_READ", "COMPLIANCE_SERVICES_MANAGE", "INVOICE_READ", "AGRICULTURE_ADMIN"})
    @DisplayName("rights from other modules, including the client-side compliance rights, do not open it")
    void otherModulesRightsAreNotEnough() throws Exception {
        asTenant(get(url)).andExpect(status().isForbidden());

        verify(readinessService, never()).assess(any(), any());
    }

    @Test
    @WithMockUser(authorities = "COMPLIANCE_READ")
    @DisplayName("an unknown tender is a 404")
    void unknownTenderIs404() throws Exception {
        when(readinessService.assess(any(), eq(tenderId))).thenThrow(new ResourceNotFoundException("Tender", tenderId.toString()));

        asTenant(get(url)).andExpect(status().isNotFound());
    }
}
