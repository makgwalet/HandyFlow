package za.co.handyflow.platform.clinic.api;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import za.co.handyflow.platform.clinic.application.internal.ClinicVisitStageService;
import za.co.handyflow.platform.clinic.dto.VisitStageDtos.Stage;
import za.co.handyflow.platform.clinic.dto.VisitStageDtos.Stages;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ClinicVisitStageController.class)
@Import(za.co.handyflow.platform.WebMvcTestSecuritySupport.class)
class ClinicVisitStageControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean ClinicVisitStageService service;

    static final String URL = "/api/v1/clinic/visit-types/ANTENATAL/stages";

    @BeforeEach
    void setTenant() { za.co.handyflow.platform.shared.TenantContext.setTenantId("9ecb3dc7-75d4-4e56-b0a2-c95d3c7c584f"); }

    @AfterEach
    void clearTenant() { za.co.handyflow.platform.shared.TenantContext.clear(); }

    private Stages antenatal() {
        return new Stages("ANTENATAL", "PLATFORM", List.of(new Stage("SYMPTOMS", true), new Stage("EXAMINATION", true),
                new Stage("DIAGNOSIS", true), new Stage("PLAN", true)));
    }

    @Test
    @WithMockUser(authorities = "CLINIC_QUESTIONNAIRE_READ")
    @DisplayName("GET stages is open to anyone who can open questionnaires and returns the four stages")
    void getReturnsStages() throws Exception {
        when(service.get(any(TenantId.class), eq("ANTENATAL"))).thenReturn(antenatal());

        mvc.perform(get(URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.source").value("PLATFORM"))
                .andExpect(jsonPath("$.data.stages", hasSize(4)))
                .andExpect(jsonPath("$.data.stages[1].stage").value("EXAMINATION"));
    }

    @Test
    @DisplayName("GET stages without auth is refused")
    void getWithoutAuthRefused() throws Exception {
        mvc.perform(get(URL)).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "CLINIC_READ")
    @DisplayName("PUT stages needs the content-admin permission")
    void putNeedsAdmin() throws Exception {
        mvc.perform(put(URL).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"stages\":[]}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    @WithMockUser(authorities = "CLINIC_CONTENT_ADMIN")
    @DisplayName("PUT stages passes the stages to the service")
    void putSetsStages() throws Exception {
        when(service.set(any(TenantId.class), eq("ANTENATAL"), any())).thenReturn(antenatal());

        mvc.perform(put(URL).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"stages\":[{\"stage\":\"SYMPTOMS\",\"required\":true}]}"))
                .andExpect(status().isOk());

        verify(service).set(any(TenantId.class), eq("ANTENATAL"), argThat(l -> l.size() == 1 && "SYMPTOMS".equals(l.get(0).stage())));
    }

    @Test
    @WithMockUser(authorities = "CLINIC_CONTENT_ADMIN")
    @DisplayName("DELETE stages goes back to the platform default")
    void deleteClears() throws Exception {
        when(service.clear(any(TenantId.class), eq("ANTENATAL"))).thenReturn(antenatal());
        mvc.perform(delete(URL).with(csrf())).andExpect(status().isOk());
        verify(service).clear(any(TenantId.class), eq("ANTENATAL"));
    }
}
