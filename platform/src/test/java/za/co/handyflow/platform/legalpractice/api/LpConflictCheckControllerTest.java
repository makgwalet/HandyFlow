package za.co.handyflow.platform.legalpractice.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import za.co.handyflow.platform.WebMvcTestSecuritySupport;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.legalpractice.application.internal.LpConflictCheckService;
import za.co.handyflow.platform.legalpractice.dto.LpConflictCheckResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Who may run a conflict check, who may read them, and who may decide one that found something. The rule that a different
 * person decides lives in the service ({@code LpConflictCheckServiceTest}); this class covers the doors.
 */
@WebMvcTest(LpConflictCheckController.class)
@Import(WebMvcTestSecuritySupport.class)
class LpConflictCheckControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    @MockitoBean LpConflictCheckService service;
    @MockitoBean FeatureGuard featureGuard;

    static final String BASE = "/api/v1/legal-practice/conflict-checks";

    @BeforeEach
    void seedTenantContext() {
        TenantContext.setTenantId(UUID.randomUUID().toString());
        TenantContext.setUserId(UUID.randomUUID().toString());
        TenantContext.setUserName("Jane Attorney");
    }

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    LpConflictCheckResponse response(String status) {
        return new LpConflictCheckResponse(UUID.randomUUID(), UUID.randomUUID(), "Dlamini Trading", status, false, List.of(), List.of(),
                "Clerk", Instant.now(), null, null, null, false, null, null, false);
    }

    Map<String, Object> runBody() {
        return Map.of("clientId", UUID.randomUUID().toString(), "parties", List.of(Map.of("name", "Nkosi Builders", "role", "OPPOSING_PARTY")));
    }

    @Test
    @WithMockUser(authorities = "LEGALPRACTICE_CONFLICT_RUN")
    @DisplayName("POST conflict-checks with LEGALPRACTICE_CONFLICT_RUN returns 201")
    void runWithRunPermissionReturns201() throws Exception {
        when(service.run(any(), any(), any(), any())).thenReturn(response("CLEAR"));
        mvc.perform(post(BASE).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(runBody())))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(authorities = "LEGALPRACTICE_MANAGE")
    @DisplayName("POST conflict-checks with LEGALPRACTICE_MANAGE returns 201 — whoever can open a matter can run the check for it")
    void runWithManageReturns201() throws Exception {
        when(service.run(any(), any(), any(), any())).thenReturn(response("CLEAR"));
        mvc.perform(post(BASE).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(runBody())))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(authorities = {"LEGALPRACTICE_READ", "LEGALPRACTICE_CONFLICT_REVIEW"})
    @DisplayName("POST conflict-checks with only READ and REVIEW returns 403")
    void runWithoutRunPermissionReturns403() throws Exception {
        mvc.perform(post(BASE).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(runBody())))
                .andExpect(status().isForbidden());
        verify(service, never()).run(any(), any(), any(), any());
    }

    @Test
    @WithMockUser(authorities = "LEGALPRACTICE_CONFLICT_RUN")
    @DisplayName("POST conflict-checks without a client returns 400")
    void runWithoutClientReturns400() throws Exception {
        mvc.perform(post(BASE).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"parties\":[]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(authorities = "LEGALPRACTICE_CONFLICT_REVIEW")
    @DisplayName("POST decide with LEGALPRACTICE_CONFLICT_REVIEW returns 200")
    void decideWithReviewReturns200() throws Exception {
        when(service.decide(any(), any(), any(), any(), any())).thenReturn(response("CLEARED_AFTER_REVIEW"));
        mvc.perform(post(BASE + "/" + UUID.randomUUID() + "/decide").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"outcome\":\"CLEARED_AFTER_REVIEW\",\"reason\":\"Different company with a similar name\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = {"LEGALPRACTICE_MANAGE", "LEGALPRACTICE_CONFLICT_RUN"})
    @DisplayName("POST decide without the review permission returns 403 — running a check does not let you decide it")
    void decideWithoutReviewReturns403() throws Exception {
        mvc.perform(post(BASE + "/" + UUID.randomUUID() + "/decide").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"outcome\":\"CLEARED_AFTER_REVIEW\",\"reason\":\"Different company with a similar name\"}"))
                .andExpect(status().isForbidden());
        verify(service, never()).decide(any(), any(), any(), any(), any());
    }

    @Test
    @WithMockUser(authorities = "LEGALPRACTICE_CONFLICT_REVIEW")
    @DisplayName("POST decide without a reason returns 400")
    void decideWithoutReasonReturns400() throws Exception {
        mvc.perform(post(BASE + "/" + UUID.randomUUID() + "/decide").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"outcome\":\"DECLINED\",\"reason\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(authorities = "LEGALPRACTICE_CONFLICT_REVIEW")
    @DisplayName("GET conflict-checks with LEGALPRACTICE_CONFLICT_REVIEW returns 200")
    void listWithReviewReturns200() throws Exception {
        Page<LpConflictCheckResponse> page = new PageImpl<>(List.of(response("PENDING_REVIEW")));
        when(service.list(any(), any(), any(), any())).thenReturn(page);
        mvc.perform(get(BASE).param("status", "PENDING_REVIEW")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = "LEGALPRACTICE_READ")
    @DisplayName("GET conflict-checks with only LEGALPRACTICE_READ returns 403 — who the firm is acting against is not for every reader")
    void listWithReadOnlyReturns403() throws Exception {
        mvc.perform(get(BASE)).andExpect(status().isForbidden());
        mvc.perform(get(BASE + "/" + UUID.randomUUID())).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET conflict-checks with no authentication is rejected")
    void unauthenticatedIsRejected() throws Exception {
        mvc.perform(get(BASE)).andExpect(result ->
                org.assertj.core.api.Assertions.assertThat(result.getResponse().getStatus()).isIn(401, 403));
    }
}
