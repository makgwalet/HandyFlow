package za.co.handyflow.platform.trainingprovider.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import za.co.handyflow.platform.WebMvcTestSecuritySupport;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.trainingprovider.application.internal.TrainProvBillingService;
import za.co.handyflow.platform.trainingprovider.application.internal.TrainProvClientService;
import za.co.handyflow.platform.trainingprovider.application.internal.TrainProvPdfService;
import za.co.handyflow.platform.trainingprovider.domain.model.TrainProvInvoice;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Financial-commit-point gating: generating an invoice and recording a
 * payment are ADMIN-only — same convention
 * WhseBillingControllerTest/CollAgencyTrustControllerTest already
 * establish for this codebase.
 */
@WebMvcTest(TrainProvBillingController.class)
@Import(WebMvcTestSecuritySupport.class)
class TrainProvBillingControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    @MockitoBean TrainProvBillingService billingService;
    @MockitoBean TrainProvClientService clientService;
    @MockitoBean TrainProvPdfService pdfService;
    @MockitoBean JdbcTemplate jdbc;
    @MockitoBean FeatureGuard featureGuard;

    static final String BASE = "/api/v1/training-provider";

    private TrainProvInvoice testInvoice(UUID clientId) {
        return TrainProvInvoice.create(TenantId.generate(), clientId, "TPI-00001", LocalDate.now().minusMonths(1),
                LocalDate.now(), LocalDate.now(), LocalDate.now().plusDays(30), 3, new BigDecimal("4500.00"), BigDecimal.ZERO);
    }

    @Test
    @WithMockUser(authorities = "TRAININGPROVIDER_MANAGE")
    @DisplayName("POST generate with only TRAININGPROVIDER_MANAGE returns 403 — ADMIN only")
    void generateInvoiceWithManageOnlyReturns403() throws Exception {
        mvc.perform(post(BASE + "/clients/" + UUID.randomUUID() + "/invoices/generate").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"periodEnd":"2026-08-31"}
                            """))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "TRAININGPROVIDER_ADMIN")
    @DisplayName("POST generate with TRAININGPROVIDER_ADMIN returns 201")
    void generateInvoiceWithAdminReturns201() throws Exception {
        UUID clientId = UUID.randomUUID();
        when(billingService.generateInvoice(any(), any(), any())).thenReturn(testInvoice(clientId));

        mvc.perform(post(BASE + "/clients/" + clientId + "/invoices/generate").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"periodEnd":"2026-08-31"}
                            """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.invoiceNumber").value("TPI-00001"))
                .andExpect(jsonPath("$.data.total").value(4500.00));
    }

    @Test
    @WithMockUser(authorities = "TRAININGPROVIDER_MANAGE")
    @DisplayName("POST payments with only TRAININGPROVIDER_MANAGE returns 403 — ADMIN only")
    void recordPaymentWithManageOnlyReturns403() throws Exception {
        mvc.perform(post(BASE + "/invoices/" + UUID.randomUUID() + "/payments").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"amount":1000.00}
                            """))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "TRAININGPROVIDER_READ")
    @DisplayName("GET /invoices/{id} returns 200 with TRAININGPROVIDER_READ")
    void getReturns200WithRead() throws Exception {
        UUID clientId = UUID.randomUUID();
        TrainProvInvoice invoice = testInvoice(clientId);
        when(billingService.get(any(), any())).thenReturn(invoice);

        mvc.perform(get(BASE + "/invoices/" + invoice.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DRAFT"));
    }

    @Test
    @WithMockUser(authorities = "SOME_OTHER_AUTHORITY")
    @DisplayName("GET /invoices/{id} returns 403 without any TRAININGPROVIDER_* authority")
    void getReturns403WithoutAuthority() throws Exception {
        mvc.perform(get(BASE + "/invoices/" + UUID.randomUUID())).andExpect(status().isForbidden());
    }
}
