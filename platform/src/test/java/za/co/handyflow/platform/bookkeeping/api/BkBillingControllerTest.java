package za.co.handyflow.platform.bookkeeping.api;

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
import za.co.handyflow.platform.bookkeeping.application.internal.BkBillingService;
import za.co.handyflow.platform.bookkeeping.application.internal.BkPdfService;
import za.co.handyflow.platform.bookkeeping.dto.BkInvoiceResponse;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Invoice generation and payment recording are financially-critical —
 * ADMIN-only, matching this module's own equivalent of every other
 * provider module's own billing-controller gating (FmBillingControllerTest
 * etc.). READ/MANAGE can list and view invoices but not generate or mutate them.
 */
@WebMvcTest(BkBillingController.class)
@Import(WebMvcTestSecuritySupport.class)
class BkBillingControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    @MockitoBean BkBillingService billingService;
    @MockitoBean BkPdfService pdfService;
    @MockitoBean FeatureGuard featureGuard;

    static final String BASE = "/api/v1/bookkeeping";

    private BkInvoiceResponse testResponse() {
        LocalDate issue = LocalDate.now();
        return new BkInvoiceResponse(UUID.randomUUID(), UUID.randomUUID(), "INV-00001", issue.minusMonths(1), issue,
                issue, issue.plusDays(30), new BigDecimal("1000.00"), BigDecimal.ZERO, new BigDecimal("1000.00"),
                BigDecimal.ZERO, new BigDecimal("1000.00"), "DRAFT", Instant.now());
    }

    @Test
    @WithMockUser(authorities = "BOOKKEEPING_MANAGE")
    @DisplayName("POST clients/{id}/invoices/generate with only BOOKKEEPING_MANAGE returns 403 — ADMIN only")
    void generateInvoiceWithManageOnlyReturns403() throws Exception {
        mvc.perform(post(BASE + "/clients/" + UUID.randomUUID() + "/invoices/generate").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"periodStart":"2026-01-01","periodEnd":"2026-01-31"}
                            """))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "BOOKKEEPING_ADMIN")
    @DisplayName("POST clients/{id}/invoices/generate with BOOKKEEPING_ADMIN returns 201")
    void generateInvoiceWithAdminReturns201() throws Exception {
        when(billingService.generateInvoice(any(), any(), any(), any())).thenReturn(testResponse());

        mvc.perform(post(BASE + "/clients/" + UUID.randomUUID() + "/invoices/generate").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"periodStart":"2026-01-01","periodEnd":"2026-01-31"}
                            """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.invoiceNumber").value("INV-00001"));
    }

    @Test
    @WithMockUser(authorities = "BOOKKEEPING_MANAGE")
    @DisplayName("POST invoices/{id}/payments with only BOOKKEEPING_MANAGE returns 403 — ADMIN only")
    void recordPaymentWithManageOnlyReturns403() throws Exception {
        mvc.perform(post(BASE + "/invoices/" + UUID.randomUUID() + "/payments").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"amount":500.00}
                            """))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "BOOKKEEPING_ADMIN")
    @DisplayName("POST invoices/{id}/payments with BOOKKEEPING_ADMIN returns 200")
    void recordPaymentWithAdminReturns200() throws Exception {
        when(billingService.recordPayment(any(), any(), any())).thenReturn(testResponse());

        mvc.perform(post(BASE + "/invoices/" + UUID.randomUUID() + "/payments").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"amount":500.00}
                            """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DRAFT"));
    }

    @Test
    @WithMockUser(authorities = "BOOKKEEPING_READ")
    @DisplayName("GET invoices with BOOKKEEPING_READ returns 200 (reads are not ADMIN-gated)")
    void listInvoicesWithReadReturns200() throws Exception {
        mvc.perform(get(BASE + "/invoices").param("clientId", UUID.randomUUID().toString()))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = "SOME_OTHER_AUTHORITY")
    @DisplayName("GET invoices returns 403 without any BOOKKEEPING_* authority")
    void listInvoicesReturns403WithoutAuthority() throws Exception {
        mvc.perform(get(BASE + "/invoices").param("clientId", UUID.randomUUID().toString()))
                .andExpect(status().isForbidden());
    }
}
