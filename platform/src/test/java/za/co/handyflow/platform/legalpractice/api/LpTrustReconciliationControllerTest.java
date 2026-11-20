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
import za.co.handyflow.platform.legalpractice.application.internal.LpTrustReconciliationService;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Who may prepare, who may approve, who may only look. The rule that a different person approves lives in the service. */
@WebMvcTest(LpTrustReconciliationController.class)
@Import(WebMvcTestSecuritySupport.class)
class LpTrustReconciliationControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    @MockitoBean LpTrustReconciliationService service;
    @MockitoBean FeatureGuard featureGuard;

    static final String BASE = "/api/v1/legal-practice/trust-reconciliations";
    static final String START = "{\"periodEnd\":\"2026-10-31\",\"statementBalance\":125000.50}";
    static final String ITEM = "{\"kind\":\"OUTSTANDING_DEPOSIT\",\"amount\":1500,\"itemDate\":\"2026-10-30\",\"description\":\"Deposit in transit\"}";

    @BeforeEach
    void seedTenantContext() {
        TenantContext.setTenantId(UUID.randomUUID().toString());
        TenantContext.setUserId(UUID.randomUUID().toString());
        TenantContext.setUserName("Jane Attorney");
    }

    @AfterEach
    void clearTenantContext() { TenantContext.clear(); }

    @Test
    @WithMockUser(authorities = "LEGALPRACTICE_TRUST_RECONCILE")
    @DisplayName("Preparing: start, statement, items and submit all work with LEGALPRACTICE_TRUST_RECONCILE")
    void preparerCanPrepare() throws Exception {
        UUID id = UUID.randomUUID();
        mvc.perform(post(BASE).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(START)).andExpect(status().isCreated());
        mvc.perform(put(BASE + "/" + id + "/statement").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"statementBalance\":100}")).andExpect(status().isOk());
        mvc.perform(post(BASE + "/" + id + "/items").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(ITEM)).andExpect(status().isCreated());
        mvc.perform(delete(BASE + "/" + id + "/items/" + UUID.randomUUID()).with(csrf())).andExpect(status().isOk());
        mvc.perform(post(BASE + "/" + id + "/submit").with(csrf())).andExpect(status().isOk());
        mvc.perform(get(BASE)).andExpect(status().isOk());
        mvc.perform(get(BASE + "/" + id)).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = "LEGALPRACTICE_TRUST_RECONCILE")
    @DisplayName("Approving with only the prepare permission returns 403 — preparing does not let you approve")
    void preparerCannotApprove() throws Exception {
        mvc.perform(post(BASE + "/" + UUID.randomUUID() + "/approve").with(csrf())).andExpect(status().isForbidden());
        verify(service, never()).approve(any(), any(), any(), any(), any());
    }

    @Test
    @WithMockUser(authorities = "LEGALPRACTICE_TRUST_RECONCILE_APPROVE")
    @DisplayName("Approving and returning work with LEGALPRACTICE_TRUST_RECONCILE_APPROVE; preparing does not")
    void approverCanApproveAndReturnButNotPrepare() throws Exception {
        UUID id = UUID.randomUUID();
        mvc.perform(post(BASE + "/" + id + "/approve").with(csrf())).andExpect(status().isOk());
        mvc.perform(post(BASE + "/" + id + "/approve").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"note\":\"Checked the three differences\"}")).andExpect(status().isOk());
        mvc.perform(post(BASE + "/" + id + "/return").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Statement balance looks wrong\"}")).andExpect(status().isOk());
        mvc.perform(get(BASE + "/" + id)).andExpect(status().isOk());
        mvc.perform(post(BASE).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(START)).andExpect(status().isForbidden());
        mvc.perform(post(BASE + "/" + id + "/submit").with(csrf())).andExpect(status().isForbidden());
        mvc.perform(post(BASE + "/" + id + "/items").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(ITEM)).andExpect(status().isForbidden());
        verify(service, never()).start(any(), any(), any(), any());
        verify(service, never()).submit(any(), any(), any());
    }

    @Test
    @WithMockUser(authorities = "LEGALPRACTICE_ADMIN")
    @DisplayName("LEGALPRACTICE_ADMIN can prepare and approve")
    void adminCanDoBoth() throws Exception {
        UUID id = UUID.randomUUID();
        mvc.perform(post(BASE).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(START)).andExpect(status().isCreated());
        mvc.perform(post(BASE + "/" + id + "/approve").with(csrf())).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = {"LEGALPRACTICE_MANAGE", "LEGALPRACTICE_READ", "LEGALPRACTICE_TRUST_RECEIVE"})
    @DisplayName("Ordinary matter and trust staff cannot see, prepare or approve a reconciliation (403) but can read the lock date")
    void othersAreShutOutExceptTheLockDate() throws Exception {
        UUID id = UUID.randomUUID();
        mvc.perform(get(BASE)).andExpect(status().isForbidden());
        mvc.perform(get(BASE + "/" + id)).andExpect(status().isForbidden());
        mvc.perform(post(BASE).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(START)).andExpect(status().isForbidden());
        mvc.perform(post(BASE + "/" + id + "/approve").with(csrf())).andExpect(status().isForbidden());
        mvc.perform(post(BASE + "/" + id + "/return").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Statement balance looks wrong\"}")).andExpect(status().isForbidden());
        mvc.perform(get(BASE + "/lock")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = "LEGALPRACTICE_TRUST_RECONCILE")
    @DisplayName("Bad bodies return 400: no statement balance, no kind, no reason")
    void badBodiesAreRejected() throws Exception {
        mvc.perform(post(BASE).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"periodEnd\":\"2026-10-31\"}")).andExpect(status().isBadRequest());
        mvc.perform(post(BASE + "/" + UUID.randomUUID() + "/items").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\":5,\"itemDate\":\"2026-10-30\",\"description\":\"x y z\"}")).andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(authorities = "LEGALPRACTICE_TRUST_RECONCILE_APPROVE")
    @DisplayName("Returning without a reason returns 400")
    void returnNeedsAReason() throws Exception {
        mvc.perform(post(BASE + "/" + UUID.randomUUID() + "/return").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"\"}")).andExpect(status().isBadRequest());
    }
}
