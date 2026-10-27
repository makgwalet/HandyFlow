package za.co.handyflow.platform.facilitiesmanagement.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import za.co.handyflow.platform.WebMvcTestSecuritySupport;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.facilitiesmanagement.application.internal.FmBillingService;
import za.co.handyflow.platform.facilitiesmanagement.application.internal.FmCreditNoteService;
import za.co.handyflow.platform.facilitiesmanagement.application.internal.FmPdfService;
import za.co.handyflow.platform.facilitiesmanagement.dto.CreateFmCreditNoteRequest;
import za.co.handyflow.platform.facilitiesmanagement.dto.FmCreditNoteResponse;
import za.co.handyflow.platform.facilitiesmanagement.dto.FmInvoiceResponse;
import za.co.handyflow.platform.facilitiesmanagement.dto.RecordFmPaymentRequest;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Who may do what in billing. Preparing (generate, cancel a draft, send, record payments, prepare credit notes) is
 * ADMIN or FACILITIESMANAGEMENT_BILLING_MANAGE; approving (invoices and credit notes) is ADMIN or
 * FACILITIESMANAGEMENT_BILLING_APPROVE; reads are open to READ/MANAGE/ADMIN and both billing authorities.
 * The ordinary operational MANAGE authority is deliberately NOT enough to touch money.
 */
@WebMvcTest(FmBillingController.class)
@Import(WebMvcTestSecuritySupport.class)
class FmBillingControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    @MockitoBean FmBillingService billingService;
    @MockitoBean FmCreditNoteService creditNoteService;
    @MockitoBean FmPdfService pdfService;
    @MockitoBean FeatureGuard featureGuard;

    static final String BASE = "/api/v1/facilitiesmanagement";

    private FmInvoiceResponse testResponse() {
        LocalDate issue = LocalDate.now();
        return new FmInvoiceResponse(UUID.randomUUID(), UUID.randomUUID(), "INV-00001", issue.minusMonths(1), issue,
                issue, issue.plusDays(30), new BigDecimal("1000.00"), BigDecimal.ZERO, new BigDecimal("1000.00"),
                BigDecimal.ZERO, new BigDecimal("1000.00"), "DRAFT", Instant.now(),
                BigDecimal.ZERO, UUID.randomUUID(), null, null, "NOT_POSTED", null, null,
                BigDecimal.ZERO, false, null, null, null, null, null, null);
    }

    private FmCreditNoteResponse creditNoteResponse() {
        return new FmCreditNoteResponse(UUID.randomUUID(), "FMCN-0001", UUID.randomUUID(), "INV-00001", UUID.randomUUID(),
                LocalDate.now(), "Billing error", new BigDecimal("100.00"), new BigDecimal("15.00"), new BigDecimal("15.00"),
                new BigDecimal("115.00"), "DRAFT", UUID.randomUUID(), null, null, "NOT_POSTED", null, Instant.now());
    }

    // ── invoices ─────────────────────────────────────────────────────────────

    @Test
    @WithMockUser(authorities = "FACILITIESMANAGEMENT_MANAGE")
    @DisplayName("POST clients/{id}/invoices/generate with only FACILITIESMANAGEMENT_MANAGE returns 403")
    void generateInvoiceWithManageOnlyReturns403() throws Exception {
        mvc.perform(post(BASE + "/clients/" + UUID.randomUUID() + "/invoices/generate").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"periodStart":"2026-01-01","periodEnd":"2026-01-31"}
                            """))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "FACILITIESMANAGEMENT_BILLING_MANAGE")
    @DisplayName("POST clients/{id}/invoices/generate with FACILITIESMANAGEMENT_BILLING_MANAGE returns 201")
    void generateInvoiceWithBillingManageReturns201() throws Exception {
        when(billingService.generateInvoice(any(), any(), any(), any(), any(), any())).thenReturn(testResponse());

        mvc.perform(post(BASE + "/clients/" + UUID.randomUUID() + "/invoices/generate").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"periodStart":"2026-01-01","periodEnd":"2026-01-31"}
                            """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.invoiceNumber").value("INV-00001"));
    }

    @Test
    @WithMockUser(authorities = "FACILITIESMANAGEMENT_ADMIN")
    @DisplayName("POST clients/{id}/invoices/generate with FACILITIESMANAGEMENT_ADMIN returns 201")
    void generateInvoiceWithAdminReturns201() throws Exception {
        when(billingService.generateInvoice(any(), any(), any(), any(), any(), any())).thenReturn(testResponse());

        mvc.perform(post(BASE + "/clients/" + UUID.randomUUID() + "/invoices/generate").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"periodStart":"2026-01-01","periodEnd":"2026-01-31"}
                            """))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(authorities = "FACILITIESMANAGEMENT_BILLING_MANAGE")
    @DisplayName("POST invoices/{id}/approve with only the preparing authority returns 403")
    void approveInvoiceNeedsTheApproveAuthority() throws Exception {
        mvc.perform(post(BASE + "/invoices/" + UUID.randomUUID() + "/approve").with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "FACILITIESMANAGEMENT_BILLING_APPROVE")
    @DisplayName("POST invoices/{id}/approve with FACILITIESMANAGEMENT_BILLING_APPROVE returns 200")
    void approveInvoiceWithApproveAuthorityReturns200() throws Exception {
        when(billingService.approveInvoice(any(), any())).thenReturn(testResponse());
        mvc.perform(post(BASE + "/invoices/" + UUID.randomUUID() + "/approve").with(csrf()))
                .andExpect(status().isOk());
    }

    // ── payments ─────────────────────────────────────────────────────────────

    @Test
    @WithMockUser(authorities = "FACILITIESMANAGEMENT_MANAGE")
    @DisplayName("POST invoices/{id}/payments with only FACILITIESMANAGEMENT_MANAGE returns 403")
    void recordPaymentWithManageOnlyReturns403() throws Exception {
        mvc.perform(post(BASE + "/invoices/" + UUID.randomUUID() + "/payments").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"amount":500.00}
                            """))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "FACILITIESMANAGEMENT_ADMIN")
    @DisplayName("POST invoices/{id}/payments with FACILITIESMANAGEMENT_ADMIN returns 200")
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
    @WithMockUser(authorities = "FACILITIESMANAGEMENT_ADMIN")
    @DisplayName("An Idempotency-Key header is passed to the service as the payment's idempotency key")
    void idempotencyKeyHeaderReachesTheService() throws Exception {
        when(billingService.recordPayment(any(), any(), any())).thenReturn(testResponse());

        mvc.perform(post(BASE + "/invoices/" + UUID.randomUUID() + "/payments").with(csrf())
                        .header("Idempotency-Key", "pay-2026-11-02-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"amount":500.00,"reference":"EFT-77"}
                            """))
                .andExpect(status().isOk());

        ArgumentCaptor<RecordFmPaymentRequest> captor = ArgumentCaptor.forClass(RecordFmPaymentRequest.class);
        verify(billingService).recordPayment(any(), any(), captor.capture());
        assertEquals("pay-2026-11-02-001", captor.getValue().idempotencyKey());
        assertEquals("EFT-77", captor.getValue().reference());
    }

    @Test
    @WithMockUser(authorities = "FACILITIESMANAGEMENT_ADMIN")
    @DisplayName("A key in the body wins over the header")
    void bodyKeyWinsOverHeader() throws Exception {
        when(billingService.recordPayment(any(), any(), any())).thenReturn(testResponse());

        mvc.perform(post(BASE + "/invoices/" + UUID.randomUUID() + "/payments").with(csrf())
                        .header("Idempotency-Key", "from-header")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"amount":500.00,"idempotencyKey":"from-body"}
                            """))
                .andExpect(status().isOk());

        ArgumentCaptor<RecordFmPaymentRequest> captor = ArgumentCaptor.forClass(RecordFmPaymentRequest.class);
        verify(billingService).recordPayment(any(), any(), captor.capture());
        assertEquals("from-body", captor.getValue().idempotencyKey());
    }

    @Test
    @WithMockUser(authorities = "FACILITIESMANAGEMENT_ADMIN")
    @DisplayName("A payment must carry a positive amount")
    void paymentWithoutAnAmountIsRejected() throws Exception {
        mvc.perform(post(BASE + "/invoices/" + UUID.randomUUID() + "/payments").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"reference":"EFT-77"}
                            """))
                .andExpect(status().isBadRequest());
    }

    // ── credit notes ─────────────────────────────────────────────────────────

    @Test
    @WithMockUser(authorities = "FACILITIESMANAGEMENT_MANAGE")
    @DisplayName("POST invoices/{id}/credit-notes with only FACILITIESMANAGEMENT_MANAGE returns 403")
    void prepareCreditNoteWithManageOnlyReturns403() throws Exception {
        mvc.perform(post(BASE + "/invoices/" + UUID.randomUUID() + "/credit-notes").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"subtotal":100.00,"reason":"Billing error"}
                            """))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "FACILITIESMANAGEMENT_BILLING_MANAGE")
    @DisplayName("POST invoices/{id}/credit-notes with FACILITIESMANAGEMENT_BILLING_MANAGE returns 201")
    void prepareCreditNoteWithBillingManageReturns201() throws Exception {
        when(creditNoteService.create(any(), any(), any(CreateFmCreditNoteRequest.class))).thenReturn(creditNoteResponse());

        mvc.perform(post(BASE + "/invoices/" + UUID.randomUUID() + "/credit-notes").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"subtotal":100.00,"reason":"Billing error"}
                            """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.creditNoteNumber").value("FMCN-0001"));
    }

    @Test
    @WithMockUser(authorities = "FACILITIESMANAGEMENT_BILLING_MANAGE")
    @DisplayName("A credit note without a reason is rejected")
    void creditNoteNeedsAReason() throws Exception {
        mvc.perform(post(BASE + "/invoices/" + UUID.randomUUID() + "/credit-notes").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"subtotal":100.00}
                            """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(authorities = "FACILITIESMANAGEMENT_BILLING_MANAGE")
    @DisplayName("POST credit-notes/{id}/approve with only the preparing authority returns 403")
    void approveCreditNoteNeedsTheApproveAuthority() throws Exception {
        mvc.perform(post(BASE + "/credit-notes/" + UUID.randomUUID() + "/approve").with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "FACILITIESMANAGEMENT_BILLING_APPROVE")
    @DisplayName("POST credit-notes/{id}/approve with FACILITIESMANAGEMENT_BILLING_APPROVE returns 200")
    void approveCreditNoteWithApproveAuthorityReturns200() throws Exception {
        when(creditNoteService.approve(any(), any())).thenReturn(creditNoteResponse());
        mvc.perform(post(BASE + "/credit-notes/" + UUID.randomUUID() + "/approve").with(csrf()))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = "FACILITIESMANAGEMENT_BILLING_MANAGE")
    @DisplayName("POST credit-notes/{id}/cancel needs a reason")
    void cancelCreditNoteNeedsAReason() throws Exception {
        mvc.perform(post(BASE + "/credit-notes/" + UUID.randomUUID() + "/cancel").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"reason":""}
                            """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(authorities = "FACILITIESMANAGEMENT_READ")
    @DisplayName("GET invoices/{id}/credit-notes with FACILITIESMANAGEMENT_READ returns 200")
    void listCreditNotesWithReadReturns200() throws Exception {
        when(creditNoteService.forInvoice(any(), any())).thenReturn(List.of(creditNoteResponse()));
        mvc.perform(get(BASE + "/invoices/" + UUID.randomUUID() + "/credit-notes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].creditNoteNumber").value("FMCN-0001"));
    }

    @Test
    @WithMockUser(authorities = "FACILITIESMANAGEMENT_READ")
    @DisplayName("GET credit-notes/{id}/credit-note.pdf checks access first, then returns the PDF")
    void creditNotePdfChecksAccessThenReturnsThePdf() throws Exception {
        UUID id = UUID.randomUUID();
        when(pdfService.generateCreditNotePdf(any(), eq(id))).thenReturn(new byte[]{'%', 'P', 'D', 'F'});

        mvc.perform(get(BASE + "/credit-notes/" + id + "/credit-note.pdf"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF));
        verify(creditNoteService).requireAccessible(any(), eq(id));
    }

    // ── reads ────────────────────────────────────────────────────────────────

    @Test
    @WithMockUser(authorities = "FACILITIESMANAGEMENT_READ")
    @DisplayName("GET invoices with FACILITIESMANAGEMENT_READ returns 200 (reads are not billing-gated)")
    void listInvoicesWithReadReturns200() throws Exception {
        mvc.perform(get(BASE + "/invoices").param("clientId", UUID.randomUUID().toString()))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = "SOME_OTHER_AUTHORITY")
    @DisplayName("GET invoices returns 403 without any FACILITIESMANAGEMENT_* authority")
    void listInvoicesReturns403WithoutAuthority() throws Exception {
        mvc.perform(get(BASE + "/invoices").param("clientId", UUID.randomUUID().toString()))
                .andExpect(status().isForbidden());
    }
}
