package za.co.handyflow.platform.facilitiesmanagement.api;

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
import za.co.handyflow.platform.evidence.application.EvidenceFacade;
import za.co.handyflow.platform.facilitiesmanagement.application.internal.FmPdfService;
import za.co.handyflow.platform.facilitiesmanagement.application.internal.FmWorkOrderService;
import za.co.handyflow.platform.facilitiesmanagement.dto.FmWorkOrderResponse;

import java.time.LocalDate;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(FmWorkOrderController.class)
@Import(WebMvcTestSecuritySupport.class)
class FmWorkOrderControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    @MockitoBean FmWorkOrderService workOrderService;
    @MockitoBean FmPdfService pdfService;
    @MockitoBean EvidenceFacade evidenceFacade;
    @MockitoBean FeatureGuard featureGuard;

    static final String BASE = "/api/v1/facilitiesmanagement/work-orders";

    private FmWorkOrderResponse testResponse() {
        return new FmWorkOrderResponse(UUID.randomUUID(), "WO-00001", UUID.randomUUID(), UUID.randomUUID(), null, null,
                "CORRECTIVE", "NORMAL", "OPEN", "Leaking tap", "Jane", null, null, null, null,
                LocalDate.now(), null, null, null, false, null, java.time.Instant.now());
    }

    @Test
    @WithMockUser(authorities = "FACILITIESMANAGEMENT_READ")
    @DisplayName("POST work-orders with only FACILITIESMANAGEMENT_READ returns 403")
    void createWithReadOnlyReturns403() throws Exception {
        mvc.perform(post(BASE).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"clientId":"%s","siteId":"%s","category":"CORRECTIVE","description":"Leaking tap"}
                            """.formatted(UUID.randomUUID(), UUID.randomUUID())))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "FACILITIESMANAGEMENT_MANAGE")
    @DisplayName("POST work-orders with FACILITIESMANAGEMENT_MANAGE returns 201")
    void createWithManageReturns201() throws Exception {
        when(workOrderService.createWorkOrder(any(), any())).thenReturn(testResponse());

        mvc.perform(post(BASE).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"clientId":"%s","siteId":"%s","category":"CORRECTIVE","description":"Leaking tap"}
                            """.formatted(UUID.randomUUID(), UUID.randomUUID())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.workOrderNumber").value("WO-00001"));
    }

    @Test
    @WithMockUser(authorities = "SOME_OTHER_AUTHORITY")
    @DisplayName("GET work-orders returns 403 without any FACILITIESMANAGEMENT_* authority")
    void getReturns403WithoutAuthority() throws Exception {
        mvc.perform(get(BASE)).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "FACILITIESMANAGEMENT_READ")
    @DisplayName("GET work-orders/{id} returns 200 with FACILITIESMANAGEMENT_READ")
    void getReturns200WithRead() throws Exception {
        FmWorkOrderResponse resp = testResponse();
        when(workOrderService.getWorkOrder(any(), any())).thenReturn(resp);

        mvc.perform(get(BASE + "/" + resp.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("OPEN"));
    }

    @Test
    @WithMockUser(authorities = "FACILITIESMANAGEMENT_MANAGE")
    @DisplayName("POST work-orders/{id}/assign with a vendor-only payload succeeds")
    void assignToVendorSucceeds() throws Exception {
        when(workOrderService.assign(any(), any(), any())).thenReturn(testResponse());
        UUID id = UUID.randomUUID();

        mvc.perform(post(BASE + "/" + id + "/assign").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"vendorId":"%s","vendorName":"Acme Elevators"}
                            """.formatted(UUID.randomUUID())))
                .andExpect(status().isOk());
    }
}
