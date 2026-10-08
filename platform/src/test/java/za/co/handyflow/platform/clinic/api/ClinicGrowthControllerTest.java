package za.co.handyflow.platform.clinic.api;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import za.co.handyflow.platform.clinic.application.internal.ClinicGrowthService;
import za.co.handyflow.platform.clinic.dto.GrowthDtos.GrowthChart;
import za.co.handyflow.platform.clinic.dto.GrowthDtos.SetRow;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ClinicGrowthController.class)
@Import(za.co.handyflow.platform.WebMvcTestSecuritySupport.class)
class ClinicGrowthControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean ClinicGrowthService service;
    final UUID id = UUID.randomUUID();
    final UUID patient = UUID.randomUUID();
    final UUID me = UUID.randomUUID();

    @BeforeEach void setTenant() { za.co.handyflow.platform.shared.TenantContext.setTenantId("9ecb3dc7-75d4-4e56-b0a2-c95d3c7c584f"); }
    @AfterEach void clearTenant() { za.co.handyflow.platform.shared.TenantContext.clear(); }

    RequestPostProcessor as(String... authorities) {
        return authentication(new UsernamePasswordAuthenticationToken(me.toString(), "n",
                java.util.Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList()));
    }

    SetRow set() { return new SetRow(id, "WEIGHT", "MALE", "t", "s", "v", null, null, "DRAFT", 0, null, null, null, null, null); }

    @Test @WithMockUser(authorities = "CLINIC_GROWTH_READ")
    void chartNeedsTheGrowthReadPermission() throws Exception {
        when(service.chart(any(), any())).thenReturn(new GrowthChart(patient, "MALE", 12.0, List.of(), List.of()));
        mvc.perform(get("/api/v1/clinic/patients/" + patient + "/growth")).andExpect(status().isOk());
    }

    @Test @WithMockUser(authorities = "CLINIC_READ")
    void theCoarseReadPermissionIsNotEnoughForTheChart() throws Exception {
        mvc.perform(get("/api/v1/clinic/patients/" + patient + "/growth")).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test @WithMockUser(authorities = "CLINIC_GROWTH_READ")
    void clinicianCannotSeeOrChangeReferenceData() throws Exception {
        mvc.perform(get("/api/v1/clinic/growth-reference")).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/clinic/growth-reference/" + id + "/activate").with(csrf())).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    void adminLoadsAndSubmitsButCannotApproveOrActivate() throws Exception {
        when(service.create(any(), any(), any())).thenReturn(set());
        when(service.submitForReview(any(), any())).thenReturn(set());
        mvc.perform(post("/api/v1/clinic/growth-reference").with(as("CLINIC_CONTENT_ADMIN")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"measure\":\"WEIGHT\",\"sex\":\"MALE\"}")).andExpect(status().isCreated());
        mvc.perform(post("/api/v1/clinic/growth-reference/" + id + "/submit").with(as("CLINIC_CONTENT_ADMIN")).with(csrf())).andExpect(status().isOk());
        mvc.perform(post("/api/v1/clinic/growth-reference/" + id + "/approve").with(as("CLINIC_CONTENT_ADMIN")).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/clinic/growth-reference/" + id + "/activate").with(as("CLINIC_CONTENT_ADMIN")).with(csrf())).andExpect(status().isForbidden());
    }

    @Test
    void reviewerApprovesAndActivatesWithTheirOwnId() throws Exception {
        when(service.approve(any(), any(), any())).thenReturn(set());
        when(service.activate(any(), any(), any())).thenReturn(set());
        mvc.perform(post("/api/v1/clinic/growth-reference/" + id + "/approve").with(as("CLINIC_CONTENT_APPROVE")).with(csrf())).andExpect(status().isOk());
        mvc.perform(post("/api/v1/clinic/growth-reference/" + id + "/activate").with(as("CLINIC_CONTENT_APPROVE")).with(csrf())).andExpect(status().isOk());
        verify(service).approve(any(), org.mockito.ArgumentMatchers.eq(me), org.mockito.ArgumentMatchers.eq(id));
        verify(service).activate(any(), org.mockito.ArgumentMatchers.eq(me), org.mockito.ArgumentMatchers.eq(id));
    }

    @Test
    void rulesViolationsComeBackAs4xxNot500() throws Exception {
        when(service.activate(any(), any(), any())).thenThrow(new IllegalStateException("The person who reviewed the set cannot approve and activate it"));
        mvc.perform(post("/api/v1/clinic/growth-reference/" + id + "/activate").with(as("CLINIC_CONTENT_APPROVE")).with(csrf()))
                .andExpect(status().is4xxClientError());
    }
}
