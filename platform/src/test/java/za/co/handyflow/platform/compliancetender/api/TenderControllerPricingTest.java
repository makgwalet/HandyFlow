package za.co.handyflow.platform.compliancetender.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import za.co.handyflow.platform.WebMvcTestSecuritySupport;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.compliancetender.application.internal.TenderPdfService;
import za.co.handyflow.platform.compliancetender.application.internal.TenderPersonnelService;
import za.co.handyflow.platform.compliancetender.application.internal.TenderPricingService;
import za.co.handyflow.platform.compliancetender.application.internal.TenderReadinessService;
import za.co.handyflow.platform.compliancetender.application.internal.TenderService;
import za.co.handyflow.platform.compliancetender.application.internal.TenderSnapshotService;
import za.co.handyflow.platform.compliancetender.dto.TenderPricingResponse;
import za.co.handyflow.platform.shared.ResourceNotFoundException;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Access rules, validation and error mapping for the pricing endpoints (ADR-004). Pricing is commercially sensitive: READ alone is not enough, even to look. */
@WebMvcTest(TenderController.class)
@Import(WebMvcTestSecuritySupport.class)
class TenderControllerPricingTest {

    @Autowired MockMvc mvc;

    @MockitoBean TenderService tenderService;
    @MockitoBean TenderPersonnelService personnelService;
    @MockitoBean TenderSnapshotService snapshotService;
    @MockitoBean TenderPdfService pdfService;
    @MockitoBean TenderReadinessService readinessService;
    @MockitoBean TenderPricingService pricingService;
    @MockitoBean FeatureGuard featureGuard;

    final UUID tenderId = UUID.randomUUID();
    final UUID lineId = UUID.randomUUID();
    final String base = "/api/v1/compliance/tenders/" + tenderId + "/pricing";
    final String lineUrl = "/api/v1/compliance/tenders/pricing/lines/" + lineId;
    static final String LINE = "{\"section\":\"Roads\",\"description\":\"Kerb\",\"unit\":\"m\",\"quantity\":10,\"unitCost\":25.5}";
    static final String SETTINGS = "{\"overheadPct\":10,\"contingencyPct\":5,\"profitPct\":8,\"vatApplies\":true}";

    private TenderPricingResponse response() {
        BigDecimal z = new BigDecimal("0.00");
        return new TenderPricingResponse(tenderId, "DRAFT", true, true, null,
                new TenderPricingResponse.Settings(BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.ZERO, true, new BigDecimal("15.00"), null),
                List.of(), new TenderPricingResponse.Breakdown(z, z, z, z, z, z, new BigDecimal("1428.30"), null, List.of()));
    }

    @Test
    @WithMockUser(authorities = "COMPLIANCE_MANAGE")
    @DisplayName("MANAGE reads the pricing, and the module is checked")
    void manageReads() throws Exception {
        when(pricingService.getPricing(any(), eq(tenderId))).thenReturn(response());

        mvc.perform(get(base)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.breakdown.priceInclVat").value(1428.30))
                .andExpect(jsonPath("$.data.editable").value(true));
        verify(featureGuard, atLeastOnce()).requireModule("compliancetender");
    }

    @Test
    @DisplayName("ADMIN can read and write too")
    void adminCan() throws Exception {
        when(pricingService.getPricing(any(), eq(tenderId))).thenReturn(response());
        when(pricingService.saveSettings(any(), eq(tenderId), any(), any())).thenReturn(response());

        mvc.perform(get(base).with(user("u").authorities(new SimpleGrantedAuthority("COMPLIANCE_ADMIN")))).andExpect(status().isOk());
        mvc.perform(put(base + "/settings").with(csrf()).with(user("u").authorities(new SimpleGrantedAuthority("COMPLIANCE_ADMIN")))
                .contentType(MediaType.APPLICATION_JSON).content(SETTINGS)).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = "COMPLIANCE_READ")
    @DisplayName("COMPLIANCE_READ cannot see or change pricing")
    void readIsNotEnough() throws Exception {
        mvc.perform(get(base)).andExpect(status().isForbidden());
        mvc.perform(put(base + "/settings").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(SETTINGS)).andExpect(status().isForbidden());
        mvc.perform(post(base + "/lines").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(LINE)).andExpect(status().isForbidden());
        mvc.perform(put(lineUrl).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(LINE)).andExpect(status().isForbidden());
        mvc.perform(delete(lineUrl).with(csrf())).andExpect(status().isForbidden());

        verify(pricingService, never()).getPricing(any(), any());
        verify(pricingService, never()).addLine(any(), any(), any(), any());
    }

    @Test
    @WithMockUser(authorities = {"COMPLIANCE_SERVICES_MANAGE", "INVOICE_READ"})
    @DisplayName("rights from other modules, including client-side compliance, do not open it")
    void otherRightsNotEnough() throws Exception {
        mvc.perform(get(base)).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "COMPLIANCE_MANAGE")
    @DisplayName("adding a line returns 201 with the re-priced schedule")
    void addLine() throws Exception {
        when(pricingService.addLine(any(), eq(tenderId), any(), any())).thenReturn(response());

        mvc.perform(post(base + "/lines").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(LINE))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.tenderId").value(tenderId.toString()));
    }

    @Test
    @WithMockUser(authorities = "COMPLIANCE_MANAGE")
    @DisplayName("changing and removing a line work through the line's own url")
    void updateAndDeleteLine() throws Exception {
        when(pricingService.updateLine(any(), eq(lineId), any(), any())).thenReturn(response());
        when(pricingService.deleteLine(any(), eq(lineId))).thenReturn(response());

        mvc.perform(put(lineUrl).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(LINE)).andExpect(status().isOk());
        mvc.perform(delete(lineUrl).with(csrf())).andExpect(status().isOk());
        verify(pricingService).deleteLine(any(), eq(lineId));
    }

    @Test
    @WithMockUser(authorities = "COMPLIANCE_MANAGE")
    @DisplayName("bad input is a 400 and never reaches the service: negative cost, negative quantity, blank description, percentage over 100")
    void validation() throws Exception {
        String[] badLines = {
                "{\"description\":\"x\",\"quantity\":1,\"unitCost\":-1}",
                "{\"description\":\"x\",\"quantity\":-1,\"unitCost\":1}",
                "{\"description\":\"  \",\"quantity\":1,\"unitCost\":1}",
                "{\"quantity\":1,\"unitCost\":1}",
                "{\"description\":\"x\",\"unitCost\":1}",
                "{\"description\":\"x\",\"quantity\":1}"};
        for (String bad : badLines) {
            mvc.perform(post(base + "/lines").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(bad)).andExpect(status().isBadRequest());
        }
        mvc.perform(put(base + "/settings").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"profitPct\":100.5}")).andExpect(status().isBadRequest());
        mvc.perform(put(base + "/settings").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"overheadPct\":-1}")).andExpect(status().isBadRequest());

        verify(pricingService, never()).addLine(any(), any(), any(), any());
        verify(pricingService, never()).saveSettings(any(), any(), any(), any());
    }

    @Test
    @WithMockUser(authorities = "COMPLIANCE_MANAGE")
    @DisplayName("a locked tender is a 409 and an unknown tender or line is a 404")
    void errorMapping() throws Exception {
        when(pricingService.addLine(any(), eq(tenderId), any(), any())).thenThrow(new IllegalStateException("Pricing is locked because this tender is SUBMITTED."));
        when(pricingService.getPricing(any(), eq(tenderId))).thenThrow(new ResourceNotFoundException("Tender", tenderId.toString()));
        when(pricingService.deleteLine(any(), eq(lineId))).thenThrow(new ResourceNotFoundException("TenderPricingLine", lineId.toString()));

        mvc.perform(post(base + "/lines").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(LINE)).andExpect(status().isConflict());
        mvc.perform(get(base)).andExpect(status().isNotFound());
        mvc.perform(delete(lineUrl).with(csrf())).andExpect(status().isNotFound());
    }
}
