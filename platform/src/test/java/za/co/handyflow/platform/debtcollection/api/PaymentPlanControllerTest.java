package za.co.handyflow.platform.debtcollection.api;

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
import za.co.handyflow.platform.debtcollection.application.internal.PaymentPlanService;
import za.co.handyflow.platform.debtcollection.domain.model.PaymentPlan;
import za.co.handyflow.platform.debtcollection.domain.model.PaymentPlanFrequency;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PaymentPlanController.class)
@Import(WebMvcTestSecuritySupport.class)
class PaymentPlanControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    @MockitoBean PaymentPlanService paymentPlanService;
    @MockitoBean FeatureGuard featureGuard;

    static final String BASE = "/api/v1/debtcollection/payment-plans";

    private PaymentPlan plan(TenantId tenantId) {
        return PaymentPlan.propose(tenantId, UUID.randomUUID(), new BigDecimal("1200.00"),
                new BigDecimal("300.00"), PaymentPlanFrequency.MONTHLY, LocalDate.now(), 4, null,
                UUID.randomUUID());
    }

    @Test
    @WithMockUser(authorities = "DEBTCOLLECTION_READ")
    @DisplayName("GET /payment-plans/{id} returns 200 with DEBTCOLLECTION_READ")
    void getReturns200() throws Exception {
        TenantId tenantId = TenantId.generate();
        when(paymentPlanService.get(any(), any())).thenReturn(plan(tenantId));

        mvc.perform(get(BASE + "/" + UUID.randomUUID()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }

    @Test
    @WithMockUser(authorities = "SOME_OTHER_AUTHORITY")
    @DisplayName("GET /payment-plans/{id} returns 403 without any DEBTCOLLECTION_* authority")
    void getReturns403WithoutAuthority() throws Exception {
        mvc.perform(get(BASE + "/" + UUID.randomUUID())).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "DEBTCOLLECTION_MANAGE")
    @DisplayName("POST /payment-plans/{id}/mark-installment-paid returns 200")
    void markInstallmentPaidReturns200() throws Exception {
        TenantId tenantId = TenantId.generate();
        PaymentPlan p = plan(tenantId);
        p.markInstallmentPaid();
        when(paymentPlanService.markInstallmentPaid(any(), any())).thenReturn(p);

        mvc.perform(post(BASE + "/" + UUID.randomUUID() + "/mark-installment-paid").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.installmentsPaid").value(1));
    }

    @Test
    @WithMockUser(authorities = "DEBTCOLLECTION_MANAGE")
    @DisplayName("POST /payment-plans/{id}/mark-defaulted returns 200")
    void markDefaultedReturns200() throws Exception {
        TenantId tenantId = TenantId.generate();
        PaymentPlan p = plan(tenantId);
        p.markDefaulted("missed payments");
        when(paymentPlanService.markDefaulted(any(), any(), anyString())).thenReturn(p);

        mvc.perform(post(BASE + "/" + UUID.randomUUID() + "/mark-defaulted").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"missed payments\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DEFAULTED"));
    }

    @Test
    @WithMockUser(authorities = "DEBTCOLLECTION_READ")
    @DisplayName("POST /payment-plans/{id}/cancel with only DEBTCOLLECTION_READ returns 403")
    void cancelWithReadOnlyReturns403() throws Exception {
        mvc.perform(post(BASE + "/" + UUID.randomUUID() + "/cancel").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }
}
