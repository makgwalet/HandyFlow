package za.co.handyflow.platform.clinic.api;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import za.co.handyflow.platform.clinic.application.internal.ClinicRestrictedRecordService;
import za.co.handyflow.platform.clinic.application.internal.ClinicTaskService;
import za.co.handyflow.platform.clinic.dto.TaskDtos.TaskRow;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** The task list is one of the masked worklists, so it stands in for all of them. */
@WebMvcTest(ClinicTaskController.class)
@Import({za.co.handyflow.platform.WebMvcTestSecuritySupport.class, za.co.handyflow.platform.clinic.config.ClinicRestrictedListAdvice.class})
class ClinicRestrictedListAdviceTest {

    @Autowired MockMvc mvc;
    @MockitoBean ClinicTaskService tasks;
    @MockitoBean ClinicRestrictedRecordService restricted;
    final UUID me = UUID.randomUUID(), secret = UUID.randomUUID(), open = UUID.randomUUID();

    @BeforeEach void setTenant() { za.co.handyflow.platform.shared.TenantContext.setTenantId("9ecb3dc7-75d4-4e56-b0a2-c95d3c7c584f"); }
    @AfterEach void clearTenant() { za.co.handyflow.platform.shared.TenantContext.clear(); }

    RequestPostProcessor as(String... authorities) {
        return authentication(new UsernamePasswordAuthenticationToken(me.toString(), "n",
                java.util.Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList()));
    }

    TaskRow row(UUID patient, String title) {
        return new TaskRow(UUID.randomUUID(), patient, "Ann One", null, "GENERAL", title, "private detail", null, false, "OPEN", null, null, null, Instant.now(), null, null);
    }

    @Test void maskedForSomeoneWithoutAccess() throws Exception {
        when(tasks.open(any(), any(), org.mockito.ArgumentMatchers.anyBoolean(), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(List.of(row(secret, "Follow up Ann One"), row(open, "Call Bob")));
        when(restricted.hiddenFrom(any(), any(), any())).thenReturn(Set.of(secret));
        mvc.perform(get("/api/v1/clinic/tasks").with(as("CLINIC_TASK_READ")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].title").value("Restricted record"))
                .andExpect(jsonPath("$.data[0].restricted").value(true))
                .andExpect(jsonPath("$.data[0].detail").doesNotExist())
                .andExpect(jsonPath("$.data[1].title").value("Call Bob"))
                .andExpect(jsonPath("$.data[1].detail").value("private detail"));
    }

    @Test void notMaskedWithStandingAccess() throws Exception {
        when(tasks.open(any(), any(), org.mockito.ArgumentMatchers.anyBoolean(), org.mockito.ArgumentMatchers.anyInt())).thenReturn(List.of(row(secret, "Follow up Ann One")));
        mvc.perform(get("/api/v1/clinic/tasks").with(as("CLINIC_TASK_READ", "CLINIC_RESTRICTED_RECORD_ACCESS")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].title").value("Follow up Ann One"));
        verify(restricted, never()).hiddenFrom(any(), any(), any());
    }

    @Test void listIsRefusedWhenTheCheckFails() throws Exception {
        when(tasks.open(any(), any(), org.mockito.ArgumentMatchers.anyBoolean(), org.mockito.ArgumentMatchers.anyInt())).thenReturn(List.of(row(secret, "Follow up Ann One")));
        when(restricted.hiddenFrom(any(), any(), any())).thenThrow(new RuntimeException("db down"));
        mvc.perform(get("/api/v1/clinic/tasks").with(as("CLINIC_TASK_READ"))).andExpect(status().is5xxServerError());
    }
}
