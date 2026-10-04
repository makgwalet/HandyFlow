package za.co.handyflow.platform.agriculture.api;

import org.junit.jupiter.api.AfterEach;
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
import za.co.handyflow.platform.agriculture.application.internal.AgInventoryItemService;
import za.co.handyflow.platform.agriculture.application.internal.AgPurchasesService;
import za.co.handyflow.platform.agriculture.dto.PurchasesDtos.SupplierOption;
import za.co.handyflow.platform.agriculture.dto.PurchasesDtos.SupplierSpendResponse;
import za.co.handyflow.platform.billing.FeatureGuard;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AgSupplierController.class)
@Import(WebMvcTestSecuritySupport.class)
class AgSupplierControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean AgPurchasesService purchasesService;
    @MockitoBean AgInventoryItemService inventoryService;
    @MockitoBean FeatureGuard featureGuard;

    static final String BASE = "/api/v1/agriculture";
    final UUID farmId = UUID.randomUUID(), itemId = UUID.randomUUID(), supplierId = UUID.randomUUID();

    @AfterEach
    void clearTenantContext() { TenantRequests.clear(); }

    private SupplierSpendResponse spend() { return new SupplierSpendResponse(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 9, 30), List.of(), new BigDecimal("0.00"), 0, 0, List.of("note")); }

    // ---- the supplier picker ------------------------------------------------------------------------------------------

    @Test
    @WithMockUser(authorities = {"AGRICULTURE_MANAGE", "SCM_READ"})
    @DisplayName("GET /suppliers lists active suppliers (id and name) and checks both modules")
    void listsSuppliers() throws Exception {
        when(purchasesService.suppliers(any())).thenReturn(List.of(new SupplierOption(supplierId, "AgriSupplies")));

        TenantRequests.asTenant(mvc, get(BASE + "/suppliers")).andExpect(status().isOk()).andExpect(jsonPath("$.data[0].name").value("AgriSupplies"));

        verify(featureGuard, atLeastOnce()).requireModule("agriculture");
        verify(featureGuard, atLeastOnce()).requireModule("supplychain");
    }

    @Test
    @WithMockUser(authorities = "AGRICULTURE_MANAGE")
    @DisplayName("the picker needs SCM_READ, Supply Chain's own right: managing Agriculture is not enough")
    void pickerNeedsScmRead() throws Exception {
        TenantRequests.asTenant(mvc, get(BASE + "/suppliers")).andExpect(status().isForbidden());
        verifyNoInteractions(purchasesService);
    }

    @Test
    @WithMockUser(authorities = "SCM_READ")
    @DisplayName("SCM_READ alone does not open the picker either")
    void pickerNeedsManage() throws Exception {
        TenantRequests.asTenant(mvc, get(BASE + "/suppliers")).andExpect(status().isForbidden());
        verifyNoInteractions(purchasesService);
    }

    // ---- the usual supplier -------------------------------------------------------------------------------------------

    @Test
    @WithMockUser(authorities = "AGRICULTURE_MANAGE")
    @DisplayName("PATCH /inventory-items/{id}/supplier sets the usual supplier, and asks for the supply chain module when one is given")
    void setsUsualSupplier() throws Exception {
        TenantRequests.asTenant(mvc, patch(BASE + "/inventory-items/" + itemId + "/supplier").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"supplierId\":\"" + supplierId + "\"}")).andExpect(status().isOk());

        verify(inventoryService).setUsualSupplier(any(), eq(itemId), eq(supplierId));
        verify(featureGuard, atLeastOnce()).requireModule("supplychain");
    }

    @Test
    @WithMockUser(authorities = "AGRICULTURE_MANAGE")
    @DisplayName("an empty body or a null supplier clears it, and needs no Supply Chain subscription to do so")
    void clearsUsualSupplier() throws Exception {
        TenantRequests.asTenant(mvc, patch(BASE + "/inventory-items/" + itemId + "/supplier").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"supplierId\":null}")).andExpect(status().isOk());

        verify(inventoryService).setUsualSupplier(any(), eq(itemId), isNull());
        verify(featureGuard, never()).requireModule("supplychain");
    }

    @Test
    @WithMockUser(authorities = "AGRICULTURE_READ")
    @DisplayName("reading Agriculture is not enough to change an item's supplier")
    void usualSupplierNeedsManage() throws Exception {
        TenantRequests.asTenant(mvc, patch(BASE + "/inventory-items/" + itemId + "/supplier").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"supplierId\":null}")).andExpect(status().isForbidden());
        verifyNoInteractions(inventoryService);
    }

    // ---- spend by supplier --------------------------------------------------------------------------------------------

    @Test
    @WithMockUser(authorities = {"AGRICULTURE_FINANCE", "SCM_READ"})
    @DisplayName("spend by supplier passes the dates through, and checks both modules")
    void spendBySupplier() throws Exception {
        when(purchasesService.bySupplier(any(), eq(farmId), eq(LocalDate.of(2026, 1, 1)), eq(LocalDate.of(2026, 9, 30)))).thenReturn(spend());

        TenantRequests.asTenant(mvc, get(BASE + "/farms/" + farmId + "/purchases/by-supplier").param("from", "2026-01-01").param("to", "2026-09-30"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.notes[0]").value("note"));

        verify(featureGuard, atLeastOnce()).requireModule("agriculture");
        verify(featureGuard, atLeastOnce()).requireModule("supplychain");
    }

    @Test
    @WithMockUser(authorities = "AGRICULTURE_FINANCE")
    @DisplayName("spend by supplier also needs SCM_READ")
    void spendNeedsScmRead() throws Exception {
        TenantRequests.asTenant(mvc, get(BASE + "/farms/" + farmId + "/purchases/by-supplier")).andExpect(status().isForbidden());
        verifyNoInteractions(purchasesService);
    }

    @Test
    @WithMockUser(authorities = "SCM_READ")
    @DisplayName("SCM_READ alone does not show an Agriculture farm's spend")
    void spendNeedsFinance() throws Exception {
        TenantRequests.asTenant(mvc, get(BASE + "/farms/" + farmId + "/purchases/by-supplier")).andExpect(status().isForbidden());
        verifyNoInteractions(purchasesService);
    }

    @Test
    @WithMockUser(authorities = {"AGRICULTURE_READ", "AGRICULTURE_MANAGE", "AGRICULTURE_ADMIN", "INVOICE_READ", "FUEL_MARGIN_READ"})
    @DisplayName("broad Agriculture rights and other modules' cost rights do not open supplier spend")
    void spendBroadRightsAreNotEnough() throws Exception {
        TenantRequests.asTenant(mvc, get(BASE + "/farms/" + farmId + "/purchases/by-supplier")).andExpect(status().isForbidden());
        verifyNoInteractions(purchasesService);
    }
}
