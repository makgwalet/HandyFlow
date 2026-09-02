package za.co.handyflow.platform.collectionsagency.api;

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
import za.co.handyflow.platform.collectionsagency.application.internal.CollAgencyTrustTransactionService;
import za.co.handyflow.platform.collectionsagency.application.internal.CollAgencyTrustTransactionService.RemittanceResult;
import za.co.handyflow.platform.collectionsagency.domain.model.CollAgencyCommissionInvoice;
import za.co.handyflow.platform.collectionsagency.domain.model.CollAgencyTrustTransaction;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Financial-commit-point gating: recording a debtor payment is ordinary
 * MANAGE work, but processing a remittance — which clears a client's
 * whole trust balance and posts real GL revenue — is ADMIN-only. This is
 * the one test class specifically covering that distinction, per the
 * "especially the remittance math/gating" callout in this module's own
 * build plan.
 */
@WebMvcTest(CollAgencyTrustController.class)
@Import(WebMvcTestSecuritySupport.class)
class CollAgencyTrustControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    @MockitoBean CollAgencyTrustTransactionService trustService;
    @MockitoBean FeatureGuard featureGuard;

    static final String BASE = "/api/v1/collections-agency";

    @Test
    @WithMockUser(authorities = "COLLECTIONSAGENCY_MANAGE")
    @DisplayName("POST receipt with COLLECTIONSAGENCY_MANAGE returns 201")
    void recordDebtorPaymentWithManageReturns201() throws Exception {
        UUID debtorAccountId = UUID.randomUUID();
        CollAgencyTrustTransaction txn = CollAgencyTrustTransaction.receipt(UUID.randomUUID(), UUID.randomUUID(),
                debtorAccountId, new BigDecimal("100.00"), LocalDate.now(), "REF1", null, UUID.randomUUID());
        when(trustService.recordDebtorPayment(any(), any(), any(), any(), any(), any(), any())).thenReturn(txn);

        mvc.perform(post(BASE + "/debtor-accounts/" + debtorAccountId + "/trust-transactions/receipt").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"amount":100.00}
                            """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.transactionType").value("RECEIPT"));
    }

    @Test
    @WithMockUser(authorities = "COLLECTIONSAGENCY_MANAGE")
    @DisplayName("POST remittance with only COLLECTIONSAGENCY_MANAGE returns 403 — ADMIN only")
    void processRemittanceWithManageOnlyReturns403() throws Exception {
        mvc.perform(post(BASE + "/clients/" + UUID.randomUUID() + "/trust-transactions/remittance").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "COLLECTIONSAGENCY_ADMIN")
    @DisplayName("POST remittance with COLLECTIONSAGENCY_ADMIN returns 200")
    void processRemittanceWithAdminReturns200() throws Exception {
        UUID clientId = UUID.randomUUID();
        CollAgencyTrustTransaction remittanceTxn = CollAgencyTrustTransaction.remittance(UUID.randomUUID(), clientId,
                new BigDecimal("1000.00"), LocalDate.now(), "CI00001", "notes", UUID.randomUUID());
        CollAgencyCommissionInvoice invoice = CollAgencyCommissionInvoice.create(UUID.randomUUID(), clientId,
                "CI00001", "Commission", LocalDate.now(), LocalDate.now().plusDays(30), new BigDecimal("200.00"), BigDecimal.ZERO);
        RemittanceResult result = new RemittanceResult(remittanceTxn, invoice, new BigDecimal("800.00"), new BigDecimal("200.00"));
        when(trustService.processRemittance(any(), any(), any(), any(), any())).thenReturn(result);

        mvc.perform(post(BASE + "/clients/" + clientId + "/trust-transactions/remittance").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.netPaidToClient").value(800.00))
                .andExpect(jsonPath("$.data.commissionRetained").value(200.00));
    }

    @Test
    @WithMockUser(authorities = "SOME_OTHER_AUTHORITY")
    @DisplayName("GET trust-transactions returns 403 without any COLLECTIONSAGENCY_* authority")
    void listReturns403WithoutAuthority() throws Exception {
        mvc.perform(get(BASE + "/clients/" + UUID.randomUUID() + "/trust-transactions"))
                .andExpect(status().isForbidden());
    }
}
