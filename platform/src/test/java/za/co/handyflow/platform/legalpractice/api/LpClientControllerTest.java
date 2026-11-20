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
import za.co.handyflow.platform.legalpractice.application.internal.LpClientService;
import za.co.handyflow.platform.legalpractice.application.internal.LpPortalService;
import za.co.handyflow.platform.legalpractice.application.internal.LpTrustTransactionService;
import za.co.handyflow.platform.legalpractice.dto.LpClientResponse;
import za.co.handyflow.platform.legalpractice.dto.LpTrustTransactionResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code @PreAuthorize} authorization gate coverage for {@link LpClientController} —
 * the READ/MANAGE/ADMIN split, with particular focus on the trust-transaction
 * sub-resource: a RECEIPT needs LEGALPRACTICE_TRUST_RECEIVE (or the MANAGE/ADMIN that could always record one), and money
 * leaving trust is no longer posted here at all — it goes through the request-and-approve flow in
 * {@code LpTrustPaymentRequestController}.
 * Follows {@code HrControllerTest}/{@code ClinicControllerTest}'s confirmed
 * {@code @WebMvcTest} + {@link WebMvcTestSecuritySupport} + {@code @MockitoBean}
 * + {@code @WithMockUser(authorities = ...)} shape exactly.
 * <p>
 * DEVIATION, flagged explicitly: the real precedent files could not be
 * fetched in full (only partial RAG excerpts were available), so it could
 * not be directly confirmed how they populate {@code TenantContext} inside a
 * {@code @WebMvcTest} slice, given that {@code JwtAuthFilter} — the only
 * component that normally does so — never runs in these tests ({@code
 * @WithMockUser} sets the {@code SecurityContext} directly and no
 * {@code Authorization} header is ever sent, so the filter's own
 * header-parsing branch is simply skipped). Rather than guess at an
 * unconfirmed mechanism, this class seeds {@link TenantContext} explicitly in
 * {@code @BeforeEach}/clears it in {@code @AfterEach} — the same thing
 * {@code JwtAuthFilter} would have done from a real bearer token, just done
 * directly since no token exists in this slice test.
 */
@WebMvcTest(LpClientController.class)
@Import(WebMvcTestSecuritySupport.class)
class LpClientControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    @MockitoBean LpClientService clientService;
    @MockitoBean LpTrustTransactionService trustService;
    @MockitoBean LpPortalService portalService;
    @MockitoBean EvidenceFacade evidenceFacade;
    @MockitoBean FeatureGuard featureGuard;
    @MockitoBean za.co.handyflow.platform.legalpractice.application.internal.LpWallGuard walls;

    static final String BASE = "/api/v1/legal-practice/clients";
    static final UUID TENANT_ID = UUID.randomUUID();
    static final UUID USER_ID = UUID.randomUUID();

    @BeforeEach
    void seedTenantContext() {
        TenantContext.setTenantId(TENANT_ID.toString());
        TenantContext.setUserId(USER_ID.toString());
        TenantContext.setUserName("Jane Attorney");
    }

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    LpClientResponse clientResponse(UUID id) {
        return new LpClientResponse(id, "Acme Holdings (Pty) Ltd", "acme@example.co.za", "+27820000000",
                "COMPANY", "2020/123456/07", new BigDecimal("5000.00"), "ACTIVE", null,
                Instant.now(), Instant.now());
    }

    LpTrustTransactionResponse trustResponse(UUID id, String type) {
        return new LpTrustTransactionResponse(id, UUID.randomUUID(), null, type,
                new BigDecimal("1000.00"), java.time.LocalDate.now(), null, null, null,
                USER_ID, "Jane Attorney", null, new BigDecimal("4000.00"), null, Instant.now());
    }

    // ── GET /clients — READ/MANAGE/ADMIN all allowed ────────────────────────

    @Test
    @WithMockUser(authorities = "LEGALPRACTICE_READ")
    @DisplayName("GET /clients with LEGALPRACTICE_READ returns 200")
    void getClientsWithReadReturns200() throws Exception {
        when(clientService.listClients(any(), any()))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(java.util.List.of(clientResponse(UUID.randomUUID()))));

        mvc.perform(get(BASE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", org.hamcrest.Matchers.hasSize(1)));
    }

    @Test
    @WithMockUser(authorities = "SOME_UNRELATED_AUTHORITY")
    @DisplayName("GET /clients with an unrelated authority returns 403")
    void getClientsWithUnrelatedAuthorityReturns403() throws Exception {
        mvc.perform(get(BASE)).andExpect(status().isForbidden());
    }

    // ── POST /clients — MANAGE/ADMIN, not READ ──────────────────────────────

    @Test
    @WithMockUser(authorities = "LEGALPRACTICE_READ")
    @DisplayName("POST /clients with only LEGALPRACTICE_READ returns 403")
    void createClientWithReadOnlyReturns403() throws Exception {
        var body = Map.of("name", "Acme Holdings (Pty) Ltd", "clientType", "COMPANY");

        mvc.perform(post(BASE).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(body)))
                .andExpect(status().isForbidden());

        verify(clientService, never()).createClient(any(), any());
    }

    @Test
    @WithMockUser(authorities = "LEGALPRACTICE_MANAGE")
    @DisplayName("POST /clients with LEGALPRACTICE_MANAGE returns 201")
    void createClientWithManageReturns201() throws Exception {
        var id = UUID.randomUUID();
        when(clientService.createClient(any(), any())).thenReturn(clientResponse(id));

        var body = Map.of("name", "Acme Holdings (Pty) Ltd", "clientType", "COMPANY");

        mvc.perform(post(BASE).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.clientType").value("COMPANY"));
    }

    @Test
    @WithMockUser(authorities = "LEGALPRACTICE_ADMIN")
    @DisplayName("POST /clients with LEGALPRACTICE_ADMIN returns 201 (the broader authority also satisfies MANAGE-or-ADMIN)")
    void createClientWithAdminReturns201() throws Exception {
        var id = UUID.randomUUID();
        when(clientService.createClient(any(), any())).thenReturn(clientResponse(id));

        var body = Map.of("name", "Acme Holdings (Pty) Ltd", "clientType", "COMPANY");

        mvc.perform(post(BASE).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(body)))
                .andExpect(status().isCreated());
    }

    // ── DELETE /clients/{id} — ADMIN only ───────────────────────────────────

    @Test
    @WithMockUser(authorities = "LEGALPRACTICE_MANAGE")
    @DisplayName("DELETE /clients/{id} with only LEGALPRACTICE_MANAGE returns 403 — hard delete is ADMIN-only")
    void deleteClientWithManageOnlyReturns403() throws Exception {
        var id = UUID.randomUUID();

        mvc.perform(delete(BASE + "/" + id).with(csrf()))
                .andExpect(status().isForbidden());

        verify(clientService, never()).deleteClient(any(), any());
    }

    @Test
    @WithMockUser(authorities = "LEGALPRACTICE_ADMIN")
    @DisplayName("DELETE /clients/{id} with LEGALPRACTICE_ADMIN returns 200")
    void deleteClientWithAdminReturns200() throws Exception {
        var id = UUID.randomUUID();

        mvc.perform(delete(BASE + "/" + id).with(csrf()))
                .andExpect(status().isOk());

        verify(clientService).deleteClient(any(), org.mockito.ArgumentMatchers.eq(id));
    }

    // ── POST /clients/{id}/trust-transactions/receipt — MANAGE-gated ───────

    @Test
    @WithMockUser(authorities = "LEGALPRACTICE_READ")
    @DisplayName("POST .../trust-transactions/receipt with only LEGALPRACTICE_READ returns 403")
    void recordReceiptWithReadOnlyReturns403() throws Exception {
        var id = UUID.randomUUID();
        var body = Map.of("amount", 1000);

        mvc.perform(post(BASE + "/" + id + "/trust-transactions/receipt").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(body)))
                .andExpect(status().isForbidden());

        verify(trustService, never()).recordReceipt(any(), any(), any(), any(), any());
    }

    @Test
    @WithMockUser(authorities = "LEGALPRACTICE_MANAGE")
    @DisplayName("POST .../trust-transactions/receipt with LEGALPRACTICE_MANAGE returns 201 — a plain deposit is MANAGE, not ADMIN")
    void recordReceiptWithManageReturns201() throws Exception {
        var id = UUID.randomUUID();
        when(trustService.recordReceipt(any(), any(), any(), any(), any()))
                .thenReturn(trustResponse(UUID.randomUUID(), "RECEIPT"));

        var body = Map.of("amount", 1000);

        mvc.perform(post(BASE + "/" + id + "/trust-transactions/receipt").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.transactionType").value("RECEIPT"));
    }

    // ── POST .../trust-transactions/receipt — TRUST_RECEIVE, or the MANAGE/ADMIN that could always record one ──

    @Test
    @WithMockUser(authorities = "LEGALPRACTICE_TRUST_RECEIVE")
    @DisplayName("POST .../trust-transactions/receipt with only LEGALPRACTICE_TRUST_RECEIVE returns 201 — a clerk can record deposits without MANAGE")
    void recordReceiptWithTrustReceiveReturns201() throws Exception {
        var id = UUID.randomUUID();
        when(trustService.recordReceipt(any(), any(), any(), any(), any()))
                .thenReturn(trustResponse(UUID.randomUUID(), "RECEIPT"));

        mvc.perform(post(BASE + "/" + id + "/trust-transactions/receipt").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("amount", 1000))))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(authorities = {"LEGALPRACTICE_TRUST_PAYMENT_REQUEST", "LEGALPRACTICE_TRUST_PAYMENT_APPROVE"})
    @DisplayName("POST .../trust-transactions/receipt with only the payment permissions returns 403 — asking for and approving payments is not recording deposits")
    void recordReceiptWithPaymentPermissionsOnlyReturns403() throws Exception {
        var id = UUID.randomUUID();

        mvc.perform(post(BASE + "/" + id + "/trust-transactions/receipt").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("amount", 1000))))
                .andExpect(status().isForbidden());

        verify(trustService, never()).recordReceipt(any(), any(), any(), any(), any());
    }

    // Money leaving trust is no longer posted from this controller at all: it is requested and approved through
    // LpTrustPaymentRequestController (see LpTrustPaymentRequestControllerTest), so a client controller has no endpoint
    // that can post a transfer, a disbursement or a refund by itself.
    @Test
    @DisplayName("LpClientController no longer exposes a way to post money out of trust directly")
    void noDirectPaymentOutOfTrustEndpoint() {
        var methods = java.util.Arrays.stream(LpClientController.class.getDeclaredMethods()).map(java.lang.reflect.Method::getName).toList();
        org.assertj.core.api.Assertions.assertThat(methods).doesNotContain("transferToBusiness", "payDisbursement", "refund");
        for (var m : LpTrustTransactionService.class.getDeclaredMethods()) {
            if (java.util.Set.of("transferToBusiness", "payDisbursement", "refund").contains(m.getName())) {
                org.assertj.core.api.Assertions.assertThat(m.getParameterTypes())
                        .as(m.getName() + " must require an approval")
                        .contains(LpTrustTransactionService.TrustApproval.class);
            }
        }
    }

    // ── Unauthenticated request — no authority at all ───────────────────────

    @Test
    @DisplayName("GET /clients with no authentication returns 401 or 403 depending on the entry point")
    void getClientsUnauthenticatedIsRejected() throws Exception {
        mvc.perform(get(BASE))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    org.assertj.core.api.Assertions.assertThat(status)
                            .as("an unauthenticated request must not reach the controller")
                            .isIn(401, 403);
                });
    }
}
