package za.co.handyflow.platform.clinic.api;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import za.co.handyflow.platform.clinic.application.internal.*;
import za.co.handyflow.platform.clinic.dto.billing.ClaimMoneyDtos.AllocationResponse;
import za.co.handyflow.platform.clinic.dto.billing.ClaimMoneyDtos.Ledger;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Who may move claim money (CLINIC-DEC-001 to 003, 006). The coarse billing permissions must not be enough. */
@WebMvcTest(ClinicBillingController.class)
@Import(za.co.handyflow.platform.WebMvcTestSecuritySupport.class)
class ClinicBillingMoneyControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean ClinicBillingService billingService;
    @MockitoBean ClinicService clinicService;
    @MockitoBean ClinicPatientInvoicePdfService patientInvoicePdfService;
    @MockitoBean ClinicClaimSubmissionPdfService claimSubmissionPdfService;
    @MockitoBean ClinicClaimMoneyService money;
    @MockitoBean ClinicStatementOfAccountPdfService statementOfAccountPdfService;

    final UUID id = UUID.randomUUID();
    final String base = "/api/v1/clinic/billing/claims";
    static final String BODY = "{\"amount\":100,\"reason\":\"Scheme short-paid the tariff\"}";

    @BeforeEach void setTenant() { za.co.handyflow.platform.shared.TenantContext.setTenantId("9ecb3dc7-75d4-4e56-b0a2-c95d3c7c584f"); }
    @AfterEach void clearTenant() { za.co.handyflow.platform.shared.TenantContext.clear(); }

    @Test @WithMockUser(authorities = {"CLINIC_BILLING_WRITE", "CLINIC_CLAIM_PROGRESS", "CLINIC_PAYMENT_ALLOCATE"})
    void writeOffNeedsItsOwnPermission() throws Exception {
        mvc.perform(post(base + "/" + id + "/write-off").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isForbidden());
        verifyNoInteractions(money);
    }

    @Test @WithMockUser(authorities = "CLINIC_WRITE_OFF")
    void writeOffWorksWithIt() throws Exception {
        mvc.perform(post(base + "/" + id + "/write-off").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isOk());
        verify(money).writeOff(any(), org.mockito.ArgumentMatchers.eq(id), org.mockito.ArgumentMatchers.eq(new BigDecimal("100")), org.mockito.ArgumentMatchers.eq("Scheme short-paid the tariff"));
    }

    @Test @WithMockUser(authorities = {"CLINIC_WRITE_OFF", "CLINIC_PAYMENT_ALLOCATE"})
    void creditNoteAndVoidNeedTheReversePermission() throws Exception {
        mvc.perform(post(base + "/" + id + "/credit-note").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isForbidden());
        mvc.perform(post(base + "/" + id + "/void").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Wrong member number\"}")).andExpect(status().isForbidden());
        verifyNoInteractions(money);
    }

    @Test @WithMockUser(authorities = "CLINIC_CLAIM_REVERSE")
    void creditNoteAndVoidWorkWithIt() throws Exception {
        mvc.perform(post(base + "/" + id + "/credit-note").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isOk());
        mvc.perform(post(base + "/" + id + "/void").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Wrong member number\"}")).andExpect(status().isOk());
        verify(money).creditNote(any(), org.mockito.ArgumentMatchers.eq(id), any(), any());
        verify(money).voidClaim(any(), org.mockito.ArgumentMatchers.eq(id), any());
    }

    @Test @WithMockUser(authorities = {"CLINIC_BILLING_WRITE", "CLINIC_CLAIM_PROGRESS"})
    void allocatingNeedsItsOwnPermission() throws Exception {
        mvc.perform(post(base + "/scheme-payments").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"schemeName\":\"Discovery\",\"amount\":500,\"preview\":true}")).andExpect(status().isForbidden());
        verifyNoInteractions(money);
    }

    @Test @WithMockUser(authorities = "CLINIC_PAYMENT_ALLOCATE")
    void allocatingPreviewsAndRecords() throws Exception {
        when(money.allocate(any(), any())).thenReturn(new AllocationResponse(false, "OLDEST_FIRST", null, new BigDecimal("500"), List.of()));
        mvc.perform(post(base + "/scheme-payments").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"schemeName\":\"Discovery\",\"amount\":500,\"preview\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.method").value("OLDEST_FIRST")).andExpect(jsonPath("$.data.recorded").value(false));
    }

    @Test @WithMockUser(authorities = "CLINIC_CLAIM_READ")
    void theLedgerIsReadWithClaimRead() throws Exception {
        when(money.ledgerOf(any(), org.mockito.ArgumentMatchers.eq(id))).thenReturn(
                new Ledger(id, "PARTIAL", new BigDecimal("1000"), new BigDecimal("400"), BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("600"), List.of()));
        mvc.perform(get(base + "/" + id + "/ledger")).andExpect(status().isOk()).andExpect(jsonPath("$.data.outstanding").value(600));
    }

    @Test @WithMockUser(authorities = "CLINIC_BILLING_READ")
    void theCoarseBillingReadIsNoLongerEnoughToReadTheLedger() throws Exception {
        mvc.perform(get(base + "/" + id + "/ledger")).andExpect(status().isForbidden());
    }
}
