package za.co.handyflow.platform.clinic.api;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import za.co.handyflow.platform.clinic.application.internal.ClinicTaskService;
import za.co.handyflow.platform.clinic.dto.TaskDtos.TaskRow;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ClinicTaskController.class)
@Import(za.co.handyflow.platform.WebMvcTestSecuritySupport.class)
class ClinicTaskControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean ClinicTaskService service;
    final UUID id = UUID.randomUUID();

    @BeforeEach void setTenant() { za.co.handyflow.platform.shared.TenantContext.setTenantId("9ecb3dc7-75d4-4e56-b0a2-c95d3c7c584f"); }
    @AfterEach void clearTenant() { za.co.handyflow.platform.shared.TenantContext.clear(); }

    TaskRow row() { return new TaskRow(id, null, null, null, "GENERAL", "Call Mrs Dlamini", null, null, false, "OPEN", null, null, null, Instant.now(), null, null); }

    @Test @WithMockUser(authorities = "CLINIC_TASK_READ")
    void readingNeedsTheReadPermission() throws Exception {
        when(service.open(any(), any(), org.mockito.ArgumentMatchers.anyBoolean(), org.mockito.ArgumentMatchers.anyInt())).thenReturn(List.of(row()));
        mvc.perform(get("/api/v1/clinic/tasks")).andExpect(status().isOk()).andExpect(jsonPath("$.data[0].title").value("Call Mrs Dlamini"));
    }

    @Test @WithMockUser(authorities = "CLINIC_READ")
    void theCoarseReadPermissionIsNotEnough() throws Exception {
        mvc.perform(get("/api/v1/clinic/tasks")).andExpect(status().isForbidden());
    }

    @Test @WithMockUser(authorities = "CLINIC_TASK_READ")
    void creatingNeedsItsOwnPermission() throws Exception {
        mvc.perform(post("/api/v1/clinic/tasks").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Call Mrs Dlamini\"}")).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test @WithMockUser(authorities = "CLINIC_TASK_CREATE")
    void creatingWorks() throws Exception {
        when(service.create(any(), any(), any())).thenReturn(row());
        mvc.perform(post("/api/v1/clinic/tasks").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Call Mrs Dlamini\"}")).andExpect(status().isCreated());
    }

    @Test @WithMockUser(authorities = "CLINIC_TASK_CREATE")
    void creatingDoesNotAllowClosing() throws Exception {
        mvc.perform(post("/api/v1/clinic/tasks/" + id + "/complete").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/clinic/tasks/" + id + "/dismiss").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"note\":\"Already seen\"}")).andExpect(status().isForbidden());
    }

    @Test @WithMockUser(authorities = "CLINIC_TASK_COMPLETE")
    void completingAndDismissingWork() throws Exception {
        when(service.complete(any(), any(), any(), any())).thenReturn(row());
        when(service.dismiss(any(), any(), any(), any())).thenReturn(row());
        mvc.perform(post("/api/v1/clinic/tasks/" + id + "/complete").with(csrf())).andExpect(status().isOk());
        mvc.perform(post("/api/v1/clinic/tasks/" + id + "/dismiss").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"note\":\"Already seen\"}")).andExpect(status().isOk());
    }
}
