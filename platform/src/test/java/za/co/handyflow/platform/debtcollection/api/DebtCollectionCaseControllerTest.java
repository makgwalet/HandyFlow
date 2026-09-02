package za.co.handyflow.platform.debtcollection.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import za.co.handyflow.platform.WebMvcTestSecuritySupport;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.debtcollection.application.internal.CollectionContactLogService;
import za.co.handyflow.platform.debtcollection.application.internal.DebtCollectionCaseService;
import za.co.handyflow.platform.debtcollection.application.internal.DebtCollectionPdfService;
import za.co.handyflow.platform.debtcollection.application.internal.PaymentPlanService;
import za.co.handyflow.platform.debtcollection.domain.model.ClosureReason;
import za.co.handyflow.platform.debtcollection.domain.model.DebtCollectionCase;
import za.co.handyflow.platform.evidence.application.EvidenceFacade;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(DebtCollectionCaseController.class)
@Import(WebMvcTestSecuritySupport.class)
class DebtCollectionCaseControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    @MockitoBean DebtCollectionCaseService caseService;
    @MockitoBean CollectionContactLogService contactLogService;
    @MockitoBean PaymentPlanService paymentPlanService;
    @MockitoBean EvidenceFacade evidenceFacade;
    @MockitoBean DebtCollectionPdfService pdfService;
    @MockitoBean FeatureGuard featureGuard;

    static final String BASE = "/api/v1/debtcollection/cases";

    private DebtCollectionCase testCase(TenantId tenantId) {
        return DebtCollectionCase.open(tenantId, "DC-00001", null, "Jane Debtor", "jane@example.com", null,
                new BigDecimal("1500.00"), Set.of(UUID.randomUUID()), LocalDate.now(), null, null, null,
                UUID.randomUUID());
    }

    @Test
    @WithMockUser(authorities = "DEBTCOLLECTION_READ")
    @DisplayName("GET /cases returns 200 with DEBTCOLLECTION_READ")
    void listReturns200() throws Exception {
        TenantId tenantId = TenantId.generate();
        when(caseService.list(any(), any(), any())).thenReturn(new PageImpl<>(List.of(testCase(tenantId))));

        mvc.perform(get(BASE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.content[0].caseNumber").value("DC-00001"));
    }

    @Test
    @WithMockUser(authorities = "SOME_OTHER_AUTHORITY")
    @DisplayName("GET /cases returns 403 without any DEBTCOLLECTION_* authority")
    void listReturns403WithoutAuthority() throws Exception {
        mvc.perform(get(BASE)).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "DEBTCOLLECTION_READ")
    @DisplayName("POST /cases with only DEBTCOLLECTION_READ returns 403")
    void openWithReadOnlyReturns403() throws Exception {
        mvc.perform(post(BASE).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"debtorName":"Jane Debtor","invoiceIds":["%s"],"openedDate":"2026-01-01"}
                            """.formatted(UUID.randomUUID())))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "DEBTCOLLECTION_MANAGE")
    @DisplayName("POST /cases with DEBTCOLLECTION_MANAGE returns 201")
    void openWithManageReturns201() throws Exception {
        TenantId tenantId = TenantId.generate();
        when(caseService.open(any(), any(), anyString(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(testCase(tenantId));

        mvc.perform(post(BASE).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"debtorName":"Jane Debtor","invoiceIds":["%s"],"openedDate":"2026-01-01"}
                            """.formatted(UUID.randomUUID())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.caseNumber").value("DC-00001"));
    }

    @Test
    @WithMockUser(authorities = "DEBTCOLLECTION_MANAGE")
    @DisplayName("POST /cases with an empty invoiceIds set fails validation (400)")
    void openWithEmptyInvoiceIdsReturns400() throws Exception {
        mvc.perform(post(BASE).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"debtorName":"Jane Debtor","invoiceIds":[],"openedDate":"2026-01-01"}
                            """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(authorities = "DEBTCOLLECTION_MANAGE")
    @DisplayName("POST /cases/{id}/close returns 200")
    void closeReturns200() throws Exception {
        TenantId tenantId = TenantId.generate();
        DebtCollectionCase c = testCase(tenantId);
        c.close(ClosureReason.PAID_IN_FULL, "paid");
        when(caseService.close(any(), any(), any(), any())).thenReturn(c);

        mvc.perform(post(BASE + "/" + UUID.randomUUID() + "/close").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"PAID_IN_FULL\",\"outcomeNotes\":\"paid\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CLOSED"));
    }

    @Test
    @WithMockUser(authorities = "DEBTCOLLECTION_MANAGE")
    @DisplayName("POST /cases/{id}/write-off with only DEBTCOLLECTION_MANAGE returns 403 — requires DEBTCOLLECTION_ADMIN")
    void writeOffWithManageOnlyReturns403() throws Exception {
        mvc.perform(post(BASE + "/" + UUID.randomUUID() + "/write-off").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":100.00,\"reason\":\"uncollectable\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "DEBTCOLLECTION_ADMIN")
    @DisplayName("POST /cases/{id}/write-off with DEBTCOLLECTION_ADMIN returns 200")
    void writeOffWithAdminReturns200() throws Exception {
        TenantId tenantId = TenantId.generate();
        DebtCollectionCase c = testCase(tenantId);
        c.writeOff(new BigDecimal("1500.00"), "uncollectable");
        when(caseService.writeOff(any(), any(), any(), any())).thenReturn(c);

        mvc.perform(post(BASE + "/" + UUID.randomUUID() + "/write-off").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":1500.00,\"reason\":\"uncollectable\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("WRITTEN_OFF"));
    }

    @Test
    @WithMockUser(authorities = "DEBTCOLLECTION_MANAGE")
    @DisplayName("POST /cases/{id}/contacts records a contact and returns 201")
    void recordContactReturns201() throws Exception {
        TenantId tenantId = TenantId.generate();
        var log = za.co.handyflow.platform.debtcollection.domain.model.CollectionContactLog.record(
                tenantId, UUID.randomUUID(), LocalDate.now(),
                za.co.handyflow.platform.debtcollection.domain.model.ContactMethod.PHONE_CALL,
                za.co.handyflow.platform.debtcollection.domain.model.ContactOutcome.NO_ANSWER, null, null, null,
                UUID.randomUUID(), "Staff");
        when(contactLogService.record(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(log);

        mvc.perform(post(BASE + "/" + UUID.randomUUID() + "/contacts").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contactMethod\":\"PHONE_CALL\",\"outcome\":\"NO_ANSWER\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.outcome").value("NO_ANSWER"));
    }

    @Test
    @WithMockUser(authorities = "DEBTCOLLECTION_ADMIN")
    @DisplayName("DELETE /cases/{id} with DEBTCOLLECTION_ADMIN returns 200")
    void deleteWithAdminReturns200() throws Exception {
        mvc.perform(delete(BASE + "/" + UUID.randomUUID()).with(csrf()))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = "DEBTCOLLECTION_MANAGE")
    @DisplayName("DELETE /cases/{id} with only DEBTCOLLECTION_MANAGE returns 403")
    void deleteWithManageOnlyReturns403() throws Exception {
        mvc.perform(delete(BASE + "/" + UUID.randomUUID()).with(csrf()))
                .andExpect(status().isForbidden());
    }
}
