package za.co.handyflow.platform.warehousing.api;

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
import za.co.handyflow.platform.warehousing.application.internal.WhseBillingService;
import za.co.handyflow.platform.warehousing.application.internal.WhseClientService;
import za.co.handyflow.platform.warehousing.application.internal.WhseInventoryService;
import za.co.handyflow.platform.warehousing.application.internal.WhseItemService;
import za.co.handyflow.platform.warehousing.application.internal.WhseLocationService;
import za.co.handyflow.platform.warehousing.application.internal.WhsePdfService;
import za.co.handyflow.platform.warehousing.application.internal.WhseProfileService;
import za.co.handyflow.platform.warehousing.domain.model.WhseBillingInvoice;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Financial-commit-point gating: generating a billing invoice is the one
 * action in this module that both creates a real invoice and posts real
 * GL revenue, so it is ADMIN-only — same "financial commit point needs a
 * bigger permission than day-to-day case work" convention
 * CollAgencyTrustControllerTest's remittance-gating test already
 * establishes for this codebase.
 */
@WebMvcTest(WhseBillingController.class)
@Import(WebMvcTestSecuritySupport.class)
class WhseBillingControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    @MockitoBean WhseBillingService billingService;
    @MockitoBean WhseClientService clientService;
    @MockitoBean WhseProfileService profileService;
    @MockitoBean WhseInventoryService inventoryService;
    @MockitoBean WhseItemService itemService;
    @MockitoBean WhseLocationService locationService;
    @MockitoBean WhsePdfService pdfService;
    @MockitoBean FeatureGuard featureGuard;

    static final String BASE = "/api/v1/warehousing";

    private WhseBillingInvoice testInvoice(UUID clientId) {
        return WhseBillingInvoice.create(UUID.randomUUID(), clientId, "WHI00001", LocalDate.now().minusMonths(1),
                LocalDate.now(), LocalDate.now(), LocalDate.now().plusDays(30), new BigDecimal("300.00"),
                new BigDecimal("150.00"), BigDecimal.ZERO);
    }

    @Test
    @WithMockUser(authorities = "WAREHOUSING_MANAGE")
    @DisplayName("POST generate with only WAREHOUSING_MANAGE returns 403 — ADMIN only")
    void generateInvoiceWithManageOnlyReturns403() throws Exception {
        mvc.perform(post(BASE + "/clients/" + UUID.randomUUID() + "/billing-invoices/generate").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"periodEnd":"2026-08-31"}
                            """))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "WAREHOUSING_ADMIN")
    @DisplayName("POST generate with WAREHOUSING_ADMIN returns 201")
    void generateInvoiceWithAdminReturns201() throws Exception {
        UUID clientId = UUID.randomUUID();
        WhseBillingInvoice invoice = testInvoice(clientId);
        when(billingService.generateInvoice(any(), any(), any())).thenReturn(invoice);

        mvc.perform(post(BASE + "/clients/" + clientId + "/billing-invoices/generate").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"periodEnd":"2026-08-31"}
                            """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.invoiceNumber").value("WHI00001"))
                .andExpect(jsonPath("$.data.total").value(450.00));
    }

    @Test
    @WithMockUser(authorities = "SOME_OTHER_AUTHORITY")
    @DisplayName("GET billing-invoices returns 403 without any WAREHOUSING_* authority")
    void listReturns403WithoutAuthority() throws Exception {
        mvc.perform(get(BASE + "/clients/" + UUID.randomUUID() + "/billing-invoices"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "WAREHOUSING_READ")
    @DisplayName("GET billing-invoices/{id} returns 200 with WAREHOUSING_READ")
    void getReturns200WithRead() throws Exception {
        UUID clientId = UUID.randomUUID();
        WhseBillingInvoice invoice = testInvoice(clientId);
        when(billingService.get(any(), any())).thenReturn(invoice);

        mvc.perform(get(BASE + "/billing-invoices/" + invoice.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DRAFT"));
    }
}
