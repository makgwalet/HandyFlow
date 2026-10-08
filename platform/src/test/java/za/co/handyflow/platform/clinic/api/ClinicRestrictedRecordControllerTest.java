package za.co.handyflow.platform.clinic.api;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import za.co.handyflow.platform.clinic.application.internal.ClinicRestrictedRecordService;
import za.co.handyflow.platform.clinic.dto.RestrictedRecordDtos.Session;
import za.co.handyflow.platform.clinic.dto.RestrictedRecordDtos.Status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ClinicRestrictedRecordController.class)
@Import(za.co.handyflow.platform.WebMvcTestSecuritySupport.class)
class ClinicRestrictedRecordControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean ClinicRestrictedRecordService service;

    final UUID patient = UUID.randomUUID();
    final String base = "/api/v1/clinic/patients/" + patient;

    @BeforeEach
    void setTenant() { za.co.handyflow.platform.shared.TenantContext.setTenantId("9ecb3dc7-75d4-4e56-b0a2-c95d3c7c584f"); }

    @AfterEach
    void clearTenant() { za.co.handyflow.platform.shared.TenantContext.clear(); }

    private Status open() { return new Status(false, null, null, true, null); }

    @Test @WithMockUser(authorities = "CLINIC_PATIENT_READ")
    void status_needs_only_patient_read() throws Exception {
        when(service.status(any(), any(), any(), anyBoolean())).thenReturn(open());
        mvc.perform(get(base + "/restriction")).andExpect(status().isOk()).andExpect(jsonPath("$.data.canAccess").value(true));
    }

    @Test @WithMockUser(authorities = "CLINIC_PATIENT_READ")
    void flagging_needs_the_manage_permission() throws Exception {
        mvc.perform(put(base + "/restriction").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"category\":\"HIV\",\"reason\":\"Patient asked for it\"}")).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test @WithMockUser(authorities = "CLINIC_RESTRICTED_RECORD_MANAGE")
    void flagging_with_the_manage_permission_works() throws Exception {
        when(service.flag(any(), any(), any(), any(), any())).thenReturn(new Status(true, "HIV", Instant.now(), false, null));
        mvc.perform(put(base + "/restriction").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"category\":\"HIV\",\"reason\":\"Patient asked for it\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.restricted").value(true));
    }

    @Test @WithMockUser(authorities = "CLINIC_RESTRICTED_RECORD_MANAGE")
    void lifting_with_the_manage_permission_works() throws Exception {
        when(service.release(any(), any(), any(), any())).thenReturn(open());
        mvc.perform(delete(base + "/restriction").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Patient agreed to lift it\"}")).andExpect(status().isOk());
    }

    @Test @WithMockUser(authorities = "CLINIC_PATIENT_READ")
    void breaking_the_glass_needs_its_own_permission() throws Exception {
        mvc.perform(post(base + "/break-glass").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Emergency admission\"}")).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    void breaking_the_glass_returns_the_session() throws Exception {
        when(service.start(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new Session(UUID.randomUUID(), Instant.now(), Instant.now().plusSeconds(3600)));
        var who = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                UUID.randomUUID().toString(), "n/a", List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("CLINIC_BREAK_GLASS_VIEW")));
        mvc.perform(post(base + "/break-glass").with(csrf()).with(authentication(who)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Emergency admission\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.sessionId").exists());
    }

    @Test @WithMockUser(authorities = "CLINIC_BREAK_GLASS_VIEW")
    void the_print_record_needs_the_print_permission() throws Exception {
        mvc.perform(post(base + "/break-glass/print").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"document\":\"certificate\"}")).andExpect(status().isForbidden());
    }

    @Test @WithMockUser(authorities = "CLINIC_BREAK_GLASS_REVIEW")
    void reviewers_can_list_sessions() throws Exception {
        when(service.sessions(any(), anyBoolean(), anyInt())).thenReturn(List.of());
        mvc.perform(get("/api/v1/clinic/break-glass/sessions")).andExpect(status().isOk());
    }

    @Test @WithMockUser(authorities = "CLINIC_BREAK_GLASS_VIEW")
    void people_who_break_the_glass_cannot_review_it() throws Exception {
        mvc.perform(get("/api/v1/clinic/break-glass/sessions")).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/clinic/break-glass/sessions/" + UUID.randomUUID() + "/acknowledge").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());
    }

    @Test @WithMockUser(authorities = "CLINIC_BREAK_GLASS_REVIEW")
    void reviewers_can_acknowledge() throws Exception {
        mvc.perform(post("/api/v1/clinic/break-glass/sessions/" + UUID.randomUUID() + "/acknowledge").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"note\":\"Checked with the doctor\"}")).andExpect(status().isOk());
    }
}
