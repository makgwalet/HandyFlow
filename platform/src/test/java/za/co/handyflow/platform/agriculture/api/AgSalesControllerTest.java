package za.co.handyflow.platform.agriculture.api;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
import za.co.handyflow.platform.agriculture.application.internal.AgSalesAllocationService;
import za.co.handyflow.platform.agriculture.dto.SalesDtos.SaleLineResponse;
import za.co.handyflow.platform.agriculture.dto.SalesDtos.SalesAllocationResponse;
import za.co.handyflow.platform.agriculture.dto.SalesDtos.SalesTotalsResponse;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.shared.TenantContext;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AgSalesController.class)
@Import(WebMvcTestSecuritySupport.class)
class AgSalesControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean AgSalesAllocationService salesService;
    @MockitoBean FeatureGuard featureGuard;

    static final String BASE = "/api/v1/agriculture";
    final UUID farmId = UUID.randomUUID();
    final UUID lineId = UUID.randomUUID();
    final UUID groupId = UUID.randomUUID();

    @BeforeEach
    void seedTenantContext() {
        TenantContext.setTenantId(UUID.randomUUID().toString());
        TenantContext.setUserId(UUID.randomUUID().toString());
    }

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    private SalesAllocationResponse row() {
        return new SalesAllocationResponse(UUID.randomUUID(), farmId, UUID.randomUUID(), "INV-0042", lineId, "Broiler chicken", "ABC Foods", "GROUP", groupId,
                new BigDecimal("2100"), "kg", 1000, LocalDate.of(2026, 9, 18), null, "ACTIVE", "ISSUED", new BigDecimal("88200.00"), true, null, Instant.now());
    }

    private String body(String allocations) {
        return "{\"invoiceLineId\":\"" + lineId + "\",\"allocations\":" + allocations + "}";
    }

    private String oneShare() {
        return "[{\"targetType\":\"GROUP\",\"targetId\":\"" + groupId + "\",\"quantity\":2100,\"headCount\":1000}]";
    }

    @Test
    @WithMockUser(authorities = {"AGRICULTURE_FINANCE", "INVOICE_READ"})
    @DisplayName("GET /sales/lines passes the dates and the search text through, and checks both modules are enabled")
    void searchLines() throws Exception {
        when(salesService.searchLines(any(), eq(farmId), any(), any(), any())).thenReturn(List.of(
                new SaleLineResponse(UUID.randomUUID(), "INV-0042", "ISSUED", null, "ABC Foods", LocalDate.of(2026, 9, 18), "ZAR", lineId, "Broiler chicken", "kg",
                        new BigDecimal("2100"), new BigDecimal("42"), new BigDecimal("88200.00"), new BigDecimal("88200.00"), BigDecimal.ZERO, new BigDecimal("2100"))));

        mvc.perform(get(BASE + "/farms/" + farmId + "/sales/lines").param("from", "2026-09-01").param("to", "2026-09-30").param("q", "abc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].invoiceNumber").value("INV-0042"))
                .andExpect(jsonPath("$.data[0].remainingQuantity").value(2100));

        verify(salesService).searchLines(any(), eq(farmId), eq(LocalDate.of(2026, 9, 1)), eq(LocalDate.of(2026, 9, 30)), eq("abc"));
        verify(featureGuard, atLeastOnce()).requireModule("agriculture");
        verify(featureGuard, atLeastOnce()).requireModule("invoicing");
    }

    @Test
    @WithMockUser(authorities = {"AGRICULTURE_FINANCE", "INVOICE_READ"})
    @DisplayName("GET /sales/lines with no parameters leaves the range to the service")
    void searchDefaults() throws Exception {
        when(salesService.searchLines(any(), eq(farmId), any(), any(), any())).thenReturn(List.of());

        mvc.perform(get(BASE + "/farms/" + farmId + "/sales/lines")).andExpect(status().isOk());

        verify(salesService).searchLines(any(), eq(farmId), isNull(), isNull(), isNull());
    }

    @Test
    @WithMockUser(authorities = {"AGRICULTURE_FINANCE", "INVOICE_READ"})
    @DisplayName("POST /sales-allocations returns 201 with the allocated rows and their live revenue")
    void allocate() throws Exception {
        when(salesService.allocate(any(), eq(farmId), any(), any())).thenReturn(List.of(row()));

        mvc.perform(post(BASE + "/farms/" + farmId + "/sales-allocations").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body(oneShare())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data[0].revenue").value(88200.00))
                .andExpect(jsonPath("$.data[0].counted").value(true));
    }

    @Test
    @WithMockUser(authorities = {"AGRICULTURE_FINANCE", "INVOICE_READ"})
    @DisplayName("POST without targets, without an invoice line, or with a non-positive quantity is a 400 and never reaches the service")
    void allocateValidatesTheRequest() throws Exception {
        mvc.perform(post(BASE + "/farms/" + farmId + "/sales-allocations").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"invoiceLineId\":\"" + lineId + "\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post(BASE + "/farms/" + farmId + "/sales-allocations").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"allocations\":" + oneShare() + "}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post(BASE + "/farms/" + farmId + "/sales-allocations").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(body("[{\"targetType\":\"GROUP\",\"targetId\":\"" + groupId + "\",\"quantity\":0}]")))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(salesService);
    }

    @Test
    @WithMockUser(authorities = {"AGRICULTURE_FINANCE", "INVOICE_READ"})
    @DisplayName("GET /sales-allocations lists with a target filter, and /totals returns revenue by target")
    void listAndTotals() throws Exception {
        when(salesService.list(any(), eq(farmId), any(), any(), any())).thenReturn(new PageImpl<SalesAllocationResponse>(List.of(row())));
        when(salesService.totals(any(), eq(farmId), any(), any())).thenReturn(new SalesTotalsResponse(new BigDecimal("88200.00"), 1, 0, List.of()));

        mvc.perform(get(BASE + "/farms/" + farmId + "/sales-allocations").param("targetType", "GROUP").param("targetId", groupId.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.content[0].invoiceNumber").value("INV-0042"));
        verify(salesService).list(any(), eq(farmId), eq("GROUP"), eq(groupId), any());

        mvc.perform(get(BASE + "/farms/" + farmId + "/sales-allocations/totals")).andExpect(status().isOk()).andExpect(jsonPath("$.data.revenue").value(88200.00));
    }

    @Test
    @WithMockUser(authorities = "AGRICULTURE_FINANCE")
    @DisplayName("AGRICULTURE_FINANCE alone is not enough to read invoice data: every endpoint that returns it also needs INVOICE_READ")
    void financeAloneCannotReadInvoices() throws Exception {
        mvc.perform(get(BASE + "/farms/" + farmId + "/sales/lines")).andExpect(status().isForbidden());
        mvc.perform(get(BASE + "/farms/" + farmId + "/sales-allocations")).andExpect(status().isForbidden());
        mvc.perform(get(BASE + "/farms/" + farmId + "/sales-allocations/totals")).andExpect(status().isForbidden());
        mvc.perform(post(BASE + "/farms/" + farmId + "/sales-allocations").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body(oneShare()))).andExpect(status().isForbidden());
        verifyNoInteractions(salesService);
    }

    @Test
    @WithMockUser(authorities = {"INVOICE_READ", "AGRICULTURE_READ", "AGRICULTURE_MANAGE", "AGRICULTURE_ADMIN"})
    @DisplayName("INVOICE_READ without AGRICULTURE_FINANCE is forbidden too")
    void invoiceReadAloneIsNotEnough() throws Exception {
        mvc.perform(get(BASE + "/farms/" + farmId + "/sales/lines")).andExpect(status().isForbidden());
        mvc.perform(delete(BASE + "/sales-allocations/" + UUID.randomUUID()).with(csrf())).andExpect(status().isForbidden());
        verifyNoInteractions(salesService);
    }

    @Test
    @WithMockUser(authorities = "AGRICULTURE_FINANCE")
    @DisplayName("DELETE /sales-allocations/{id} removes an allocation with just AGRICULTURE_FINANCE (it reads no invoice data)")
    void remove() throws Exception {
        UUID id = UUID.randomUUID();

        mvc.perform(delete(BASE + "/sales-allocations/" + id).with(csrf())).andExpect(status().isNoContent());

        verify(salesService).remove(any(), eq(id), any());
    }
}
