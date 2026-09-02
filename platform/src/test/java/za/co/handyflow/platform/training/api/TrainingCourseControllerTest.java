package za.co.handyflow.platform.training.api;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.training.application.internal.TrainingCourseService;
import za.co.handyflow.platform.training.domain.model.TrainingCourse;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(TrainingCourseController.class)
@Import(WebMvcTestSecuritySupport.class)
class TrainingCourseControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    @MockitoBean TrainingCourseService courseService;
    @MockitoBean FeatureGuard featureGuard;

    static final String BASE = "/api/v1/training/courses";

    private TrainingCourse testCourse() {
        return TrainingCourse.create(TenantId.generate(), "CRS-00001", "First Aid Level 1", "desc",
                "Safety", "IN_PERSON", new BigDecimal("8"), "Jane Trainer", new BigDecimal("500"), true, 12);
    }

    @Test
    @WithMockUser(authorities = "TRAINING_READ")
    @DisplayName("GET /courses returns 200 with TRAINING_READ")
    void listCoursesReturns200() throws Exception {
        when(courseService.list(any(), any(), any(), any())).thenReturn(new PageImpl<>(List.of(testCourse())));

        mvc.perform(get(BASE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.content[0].title").value("First Aid Level 1"));
    }

    @Test
    @WithMockUser(authorities = "SOME_OTHER_AUTHORITY")
    @DisplayName("GET /courses returns 403 without any TRAINING_* authority")
    void listCoursesReturns403WithoutAuthority() throws Exception {
        mvc.perform(get(BASE)).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "TRAINING_READ")
    @DisplayName("POST /courses with only TRAINING_READ returns 403")
    void createCourseWithReadOnlyReturns403() throws Exception {
        mvc.perform(post(BASE).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"title":"First Aid Level 1"}
                            """))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "TRAINING_MANAGE")
    @DisplayName("POST /courses with TRAINING_MANAGE returns 201")
    void createCourseWithManageReturns201() throws Exception {
        when(courseService.create(any(), any(), any(), any(), any(), any(), any(), any(), anyBoolean(), any()))
                .thenReturn(testCourse());

        mvc.perform(post(BASE).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"title":"First Aid Level 1"}
                            """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.courseCode").value("CRS-00001"));
    }

    @Test
    @WithMockUser(authorities = "TRAINING_MANAGE")
    @DisplayName("DELETE /courses/{id} with only TRAINING_MANAGE returns 403 — ADMIN only")
    void deleteCourseWithManageOnlyReturns403() throws Exception {
        mvc.perform(delete(BASE + "/" + UUID.randomUUID()).with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "TRAINING_ADMIN")
    @DisplayName("DELETE /courses/{id} with TRAINING_ADMIN returns 200")
    void deleteCourseWithAdminReturns200() throws Exception {
        mvc.perform(delete(BASE + "/" + UUID.randomUUID()).with(csrf()))
                .andExpect(status().isOk());
    }
}
