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
import za.co.handyflow.platform.bookkeeping.application.internal.BkBankTransactionService;
import za.co.handyflow.platform.bookkeeping.dto.ImportBkTransactionsResponse;

import java.util.Base64;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Bank import and reconciliation are mutating, MANAGE-or-ADMIN-gated
 * operations; reads (list, match-candidates) are open to READ/MANAGE/ADMIN
 * — same authority tiers as every other module's own controller test in
 * this codebase (mirrors {@code FmBillingControllerTest}'s own gating
 * style, applied to this controller's own permission tiers).
 */
@WebMvcTest(BkBankTransactionController.class)
@Import(WebMvcTestSecuritySupport.class)
class BkBankTransactionControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    @MockitoBean BkBankTransactionService transactionService;
    @MockitoBean FeatureGuard featureGuard;

    static final String BASE = "/api/v1/bookkeeping";

    @Test
    @WithMockUser(authorities = "BOOKKEEPING_READ")
    @DisplayName("GET bank-transactions with BOOKKEEPING_READ returns 200 (reads are not MANAGE-gated)")
    void listTransactionsWithReadReturns200() throws Exception {
        mvc.perform(get(BASE + "/clients/" + UUID.randomUUID() + "/bank-transactions"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = "SOME_OTHER_AUTHORITY")
    @DisplayName("GET bank-transactions returns 403 without any BOOKKEEPING_* authority")
    void listTransactionsReturns403WithoutAuthority() throws Exception {
        mvc.perform(get(BASE + "/clients/" + UUID.randomUUID() + "/bank-transactions"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "BOOKKEEPING_READ")
    @DisplayName("POST bank-transactions/import with only BOOKKEEPING_READ returns 403 — MANAGE or ADMIN required")
    void importWithReadOnlyReturns403() throws Exception {
        String csvBase64 = Base64.getEncoder().encodeToString("Date,Description,Reference,Amount\n".getBytes());
        mvc.perform(post(BASE + "/clients/" + UUID.randomUUID() + "/bank-transactions/import").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bankAccountId\":\"" + UUID.randomUUID() + "\",\"csvBase64\":\"" + csvBase64 + "\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "BOOKKEEPING_MANAGE")
    @DisplayName("POST bank-transactions/import with BOOKKEEPING_MANAGE returns 201")
    void importWithManageReturns201() throws Exception {
        when(transactionService.importTransactions(any(), any(), any()))
                .thenReturn(new ImportBkTransactionsResponse(2, 1, 3));

        String csvBase64 = Base64.getEncoder().encodeToString("Date,Description,Reference,Amount\n".getBytes());
        mvc.perform(post(BASE + "/clients/" + UUID.randomUUID() + "/bank-transactions/import").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bankAccountId\":\"" + UUID.randomUUID() + "\",\"csvBase64\":\"" + csvBase64 + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.imported").value(2))
                .andExpect(jsonPath("$.data.skippedDuplicates").value(1));
    }

    @Test
    @WithMockUser(authorities = "BOOKKEEPING_READ")
    @DisplayName("GET match-candidates with BOOKKEEPING_READ returns 200")
    void matchCandidatesWithReadReturns200() throws Exception {
        mvc.perform(get(BASE + "/clients/" + UUID.randomUUID() + "/bank-transactions/" + UUID.randomUUID() + "/match-candidates"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = "BOOKKEEPING_READ")
    @DisplayName("POST bank-transactions/{id}/reconcile with only BOOKKEEPING_READ returns 403 — MANAGE or ADMIN required")
    void reconcileWithReadOnlyReturns403() throws Exception {
        mvc.perform(post(BASE + "/clients/" + UUID.randomUUID() + "/bank-transactions/" + UUID.randomUUID() + "/reconcile").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"journalLineId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isForbidden());
    }
}
