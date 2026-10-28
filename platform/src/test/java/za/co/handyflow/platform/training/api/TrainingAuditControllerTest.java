package za.co.handyflow.platform.training.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import za.co.handyflow.platform.WebMvcTestSecuritySupport;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.training.application.internal.TrainingAuditService;
import za.co.handyflow.platform.training.application.internal.TrainingSummaryService;
import za.co.handyflow.platform.training.dto.TrainingAuditDtos.AuditPage;
import za.co.handyflow.platform.training.dto.TrainingSummaryDtos.Summary;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({TrainingAuditController.class, TrainingSummaryController.class})
@Import(WebMvcTestSecuritySupport.class)
class TrainingAuditControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean TrainingAuditService audit;
    @MockitoBean TrainingSummaryService summaries;
    @MockitoBean FeatureGuard featureGuard;

    @Test
    @WithMockUser(authorities = "TRAINING_AUDIT")
    @DisplayName("GET /audit returns 200 with TRAINING_AUDIT")
    void auditWithPermission() throws Exception {
        when(audit.page(any(), any(), any(), any(), any(), anyInt())).thenReturn(new AuditPage(List.of(), false));
        mvc.perform(get("/api/v1/training/audit")).andExpect(status().isOk()).andExpect(jsonPath("$.data.hasMore").value(false));
    }

    @Test
    @WithMockUser(authorities = {"TRAINING_READ", "TRAINING_MANAGE", "TRAINING_ADMIN"})
    @DisplayName("The audit trail needs its own permission, not the ordinary Training ones")
    void auditWithoutPermission() throws Exception {
        mvc.perform(get("/api/v1/training/audit")).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/training/audit/export.csv")).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/training/audit/actions")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "TRAINING_READ")
    @DisplayName("GET /summary returns 200 with TRAINING_READ and 403 without any Training permission")
    void summaryPermissions() throws Exception {
        when(summaries.summary(any(), any())).thenReturn(new Summary(3, 2, 5, 1, 0, List.of(), List.of()));
        mvc.perform(get("/api/v1/training/summary")).andExpect(status().isOk()).andExpect(jsonPath("$.data.activeCourses").value(3));
    }

    @Test
    @WithMockUser(authorities = "SOMETHING_ELSE")
    void summaryRefusedWithoutPermission() throws Exception {
        mvc.perform(get("/api/v1/training/summary")).andExpect(status().isForbidden());
    }
}
