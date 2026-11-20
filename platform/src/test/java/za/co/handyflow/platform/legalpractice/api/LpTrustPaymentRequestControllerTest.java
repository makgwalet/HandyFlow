package za.co.handyflow.platform.legalpractice.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
import za.co.handyflow.platform.legalpractice.application.internal.LpTrustPaymentRequestService;
import za.co.handyflow.platform.legalpractice.application.internal.LpTrustSettingsService;
import za.co.handyflow.platform.legalpractice.dto.LpTrustPaymentRequestResponse;
import za.co.handyflow.platform.legalpractice.dto.LpTrustSettingsResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Who may ask for, approve, reject and withdraw a payment out of trust, and who may change the firm's approval rule.
 * Same {@code @WebMvcTest} shape as {@link LpClientControllerTest}. The rule itself (a different person must approve) lives
 * in the service and is covered by {@code LpTrustPaymentRequestServiceTest}; this class covers the doors.
 */
@WebMvcTest(LpTrustPaymentRequestController.class)
@Import(WebMvcTestSecuritySupport.class)
class LpTrustPaymentRequestControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    @MockitoBean LpTrustPaymentRequestService service;
    @MockitoBean LpTrustSettingsService settingsService;
    @MockitoBean FeatureGuard featureGuard;

    static final String BASE = "/api/v1/legal-practice";

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

    LpTrustPaymentRequestResponse response(String status) {
        return new LpTrustPaymentRequestResponse(UUID.randomUUID(), UUID.randomUUID(), "Dlamini Trading", null, "DISBURSEMENT_PAYMENT",
                new BigDecimal("250.00"), null, null, "Sheriff", null, null, status, UUID.randomUUID(), "Clerk", Instant.now(),
                null, null, null, false, null);
    }

    Map<String, Object> createBody() {
        return Map.of("paymentType", "DISBURSEMENT_PAYMENT", "amount", 250, "payee", "Sheriff");
    }

    // ── Asking ───────────────────────────────────────────────────────────────

    @Test
    @WithMockUser(authorities = "LEGALPRACTICE_TRUST_PAYMENT_REQUEST")
    @DisplayName("POST trust-payment-requests with LEGALPRACTICE_TRUST_PAYMENT_REQUEST returns 201")
    void requestWithRequestPermissionReturns201() throws Exception {
        when(service.create(any(), any(), any(), any(), any())).thenReturn(response("PENDING"));
        mvc.perform(post(BASE + "/clients/" + UUID.randomUUID() + "/trust-payment-requests").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(createBody())))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(authorities = "LEGALPRACTICE_ADMIN")
    @DisplayName("POST trust-payment-requests with LEGALPRACTICE_ADMIN returns 201 — admins keep the ability they had")
    void requestWithAdminReturns201() throws Exception {
        when(service.create(any(), any(), any(), any(), any())).thenReturn(response("PENDING"));
        mvc.perform(post(BASE + "/clients/" + UUID.randomUUID() + "/trust-payment-requests").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(createBody())))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(authorities = {"LEGALPRACTICE_READ", "LEGALPRACTICE_MANAGE", "LEGALPRACTICE_TRUST_RECEIVE", "LEGALPRACTICE_TRUST_PAYMENT_APPROVE"})
    @DisplayName("POST trust-payment-requests without the request permission returns 403 — MANAGE, RECEIVE and APPROVE do not let you ask")
    void requestWithoutRequestPermissionReturns403() throws Exception {
        mvc.perform(post(BASE + "/clients/" + UUID.randomUUID() + "/trust-payment-requests").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(createBody())))
                .andExpect(status().isForbidden());
        verify(service, never()).create(any(), any(), any(), any(), any());
    }

    // ── Deciding ─────────────────────────────────────────────────────────────

    @Test
    @WithMockUser(authorities = "LEGALPRACTICE_TRUST_PAYMENT_APPROVE")
    @DisplayName("POST approve with LEGALPRACTICE_TRUST_PAYMENT_APPROVE returns 200, with or without a body")
    void approveWithApprovePermissionReturns200() throws Exception {
        when(service.approve(any(), any(), any(), any(), any())).thenReturn(response("APPROVED"));
        mvc.perform(post(BASE + "/trust-payment-requests/" + UUID.randomUUID() + "/approve").with(csrf()))
                .andExpect(status().isOk());
        mvc.perform(post(BASE + "/trust-payment-requests/" + UUID.randomUUID() + "/approve").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"selfApprovalReason\":\"Sole practitioner\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = {"LEGALPRACTICE_MANAGE", "LEGALPRACTICE_TRUST_PAYMENT_REQUEST", "LEGALPRACTICE_TRUST_RECEIVE"})
    @DisplayName("POST approve and reject without the approve permission return 403 — asking does not let you approve")
    void approveAndRejectWithoutApprovePermissionReturn403() throws Exception {
        var id = UUID.randomUUID();
        mvc.perform(post(BASE + "/trust-payment-requests/" + id + "/approve").with(csrf())).andExpect(status().isForbidden());
        mvc.perform(post(BASE + "/trust-payment-requests/" + id + "/reject").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Payee does not match the invoice\"}"))
                .andExpect(status().isForbidden());
        verify(service, never()).approve(any(), any(), any(), any(), any());
        verify(service, never()).reject(any(), any(), any(), any(), any());
    }

    @Test
    @WithMockUser(authorities = "LEGALPRACTICE_TRUST_PAYMENT_APPROVE")
    @DisplayName("POST reject with a reason returns 200; without one it returns 400")
    void rejectNeedsAReason() throws Exception {
        when(service.reject(any(), any(), any(), any(), any())).thenReturn(response("REJECTED"));
        var id = UUID.randomUUID();
        mvc.perform(post(BASE + "/trust-payment-requests/" + id + "/reject").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Payee does not match the invoice\"}"))
                .andExpect(status().isOk());
        mvc.perform(post(BASE + "/trust-payment-requests/" + id + "/reject").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(authorities = "LEGALPRACTICE_TRUST_PAYMENT_REQUEST")
    @DisplayName("POST cancel with LEGALPRACTICE_TRUST_PAYMENT_REQUEST returns 200")
    void withdrawWithRequestPermissionReturns200() throws Exception {
        when(service.cancel(any(), any(), any(), any())).thenReturn(response("CANCELLED"));
        mvc.perform(post(BASE + "/trust-payment-requests/" + UUID.randomUUID() + "/cancel").with(csrf()))
                .andExpect(status().isOk());
    }

    // ── Reading ──────────────────────────────────────────────────────────────

    @Test
    @WithMockUser(authorities = "LEGALPRACTICE_TRUST_PAYMENT_APPROVE")
    @DisplayName("GET trust-payment-requests with an approve permission returns 200")
    void listWithApproveReturns200() throws Exception {
        Page<LpTrustPaymentRequestResponse> page = new PageImpl<>(List.of(response("PENDING")));
        when(service.list(any(), any(), any())).thenReturn(page);
        mvc.perform(get(BASE + "/trust-payment-requests").param("status", "PENDING")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = {"LEGALPRACTICE_READ", "LEGALPRACTICE_MANAGE"})
    @DisplayName("GET trust-payment-requests and trust-settings with only READ/MANAGE return 403 — payee and amount detail is for the people who handle trust")
    void listAndSettingsWithoutTrustPermissionsReturn403() throws Exception {
        mvc.perform(get(BASE + "/trust-payment-requests")).andExpect(status().isForbidden());
        mvc.perform(get(BASE + "/trust-settings")).andExpect(status().isForbidden());
    }

    // ── The firm's approval rule ─────────────────────────────────────────────

    @Test
    @WithMockUser(authorities = "LEGALPRACTICE_ADMIN")
    @DisplayName("PUT trust-settings with LEGALPRACTICE_ADMIN returns 200")
    void changeSettingWithAdminReturns200() throws Exception {
        when(settingsService.update(any(), org.mockito.ArgumentMatchers.anyBoolean(), any(), any(), any()))
                .thenReturn(new LpTrustSettingsResponse(true, List.of()));
        mvc.perform(put(BASE + "/trust-settings").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"selfApprovalAllowed\":true,\"reason\":\"Sole practitioner with no second signatory\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = {"LEGALPRACTICE_MANAGE", "LEGALPRACTICE_TRUST_PAYMENT_REQUEST", "LEGALPRACTICE_TRUST_PAYMENT_APPROVE"})
    @DisplayName("PUT trust-settings without LEGALPRACTICE_ADMIN returns 403 — an approver cannot switch off their own control")
    void changeSettingWithoutAdminReturns403() throws Exception {
        mvc.perform(put(BASE + "/trust-settings").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"selfApprovalAllowed\":true,\"reason\":\"Sole practitioner with no second signatory\"}"))
                .andExpect(status().isForbidden());
        verify(settingsService, never()).update(any(), org.mockito.ArgumentMatchers.anyBoolean(), any(), any(), any());
    }

    @Test
    @DisplayName("GET trust-payment-requests with no authentication is rejected")
    void unauthenticatedIsRejected() throws Exception {
        mvc.perform(get(BASE + "/trust-payment-requests")).andExpect(result ->
                org.assertj.core.api.Assertions.assertThat(result.getResponse().getStatus()).isIn(401, 403));
    }
}
