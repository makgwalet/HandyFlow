package za.co.handyflow.platform.legalpractice.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
import za.co.handyflow.platform.legalpractice.application.internal.LpDeadlineService;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Who may keep the rules and the court calendar, who may compute a deadline, and who may only read. */
@WebMvcTest(LpDeadlineController.class)
@Import(WebMvcTestSecuritySupport.class)
class LpDeadlineControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    @MockitoBean LpDeadlineService service;
    @MockitoBean FeatureGuard featureGuard;

    static final String BASE = "/api/v1/legal-practice/deadlines";

    @BeforeEach
    void seedTenantContext() {
        TenantContext.setTenantId(UUID.randomUUID().toString());
        TenantContext.setUserId(UUID.randomUUID().toString());
        TenantContext.setUserName("Jane Attorney");
    }

    @AfterEach
    void clearTenantContext() { TenantContext.clear(); }

    Map<String, Object> ruleBody() {
        return Map.ofEntries(Map.entry("code", "HC_PLEA"), Map.entry("name", "Plea"), Map.entry("triggerLabel", "Notice delivered"),
                Map.entry("keyDateType", "FILING_DEADLINE"), Map.entry("amount", 20), Map.entry("unit", "COURT_DAYS"),
                Map.entry("direction", "AFTER"), Map.entry("rollover", "NONE"));
    }

    Map<String, Object> computeBody() {
        return Map.of("ruleId", UUID.randomUUID().toString(), "triggerDate", "2026-03-06");
    }

    @Test
    @WithMockUser(authorities = "LEGALPRACTICE_DEADLINE_RULES")
    @DisplayName("POST rules with LEGALPRACTICE_DEADLINE_RULES returns 201")
    void createRuleWithRulesPermissionReturns201() throws Exception {
        mvc.perform(post(BASE + "/rules").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(ruleBody())))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(authorities = {"LEGALPRACTICE_MANAGE", "LEGALPRACTICE_DEADLINE_COMPUTE", "LEGALPRACTICE_READ"})
    @DisplayName("POST rules with MANAGE, COMPUTE and READ returns 403 — computing a date does not let you change the rules")
    void createRuleWithoutRulesPermissionReturns403() throws Exception {
        mvc.perform(post(BASE + "/rules").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(ruleBody())))
                .andExpect(status().isForbidden());
        verify(service, never()).createRule(any(), any());
    }

    @Test
    @WithMockUser(authorities = "LEGALPRACTICE_MANAGE")
    @DisplayName("PUT, activate, deactivate, install-templates and the calendar writes all return 403 for MANAGE")
    void everyRuleAndCalendarWriteIsClosedToManage() throws Exception {
        UUID id = UUID.randomUUID();
        mvc.perform(put(BASE + "/rules/" + id).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(ruleBody()))).andExpect(status().isForbidden());
        mvc.perform(post(BASE + "/rules/" + id + "/activate").with(csrf())).andExpect(status().isForbidden());
        mvc.perform(post(BASE + "/rules/" + id + "/deactivate").with(csrf())).andExpect(status().isForbidden());
        mvc.perform(post(BASE + "/rules/install-templates").with(csrf())).andExpect(status().isForbidden());
        mvc.perform(post(BASE + "/calendar").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"date\":\"2026-11-03\",\"kind\":\"PUBLIC_HOLIDAY\",\"reason\":\"Election day\"}")).andExpect(status().isForbidden());
        mvc.perform(delete(BASE + "/calendar/" + id).with(csrf())).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "LEGALPRACTICE_DEADLINE_RULES")
    @DisplayName("POST rules with a missing amount returns 400")
    void createRuleWithoutAmountReturns400() throws Exception {
        mvc.perform(post(BASE + "/rules").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"X1\",\"name\":\"X\",\"triggerLabel\":\"t\",\"keyDateType\":\"OTHER\",\"unit\":\"COURT_DAYS\",\"direction\":\"AFTER\",\"rollover\":\"NONE\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(authorities = "LEGALPRACTICE_DEADLINE_RULES")
    @DisplayName("POST calendar and DELETE calendar entry with LEGALPRACTICE_DEADLINE_RULES return 201 and 200")
    void calendarWritesWithRulesPermission() throws Exception {
        mvc.perform(post(BASE + "/calendar").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"date\":\"2026-11-03\",\"kind\":\"PUBLIC_HOLIDAY\",\"reason\":\"Election day\"}")).andExpect(status().isCreated());
        mvc.perform(delete(BASE + "/calendar/" + UUID.randomUUID()).with(csrf())).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = "LEGALPRACTICE_DEADLINE_COMPUTE")
    @DisplayName("POST compute, preview and recompute with LEGALPRACTICE_DEADLINE_COMPUTE succeed")
    void computeWithComputePermission() throws Exception {
        mvc.perform(post(BASE + "/matters/" + UUID.randomUUID() + "/compute").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(computeBody()))).andExpect(status().isCreated());
        mvc.perform(post(BASE + "/preview").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(computeBody()))).andExpect(status().isOk());
        mvc.perform(post(BASE + "/computations/" + UUID.randomUUID() + "/recompute").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"triggerDate\":\"2026-03-09\",\"reason\":\"Served on the 9th, not the 6th\"}")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = "LEGALPRACTICE_MANAGE")
    @DisplayName("POST compute with LEGALPRACTICE_MANAGE returns 201 — whoever could add a key date by hand can compute one")
    void computeWithManageReturns201() throws Exception {
        mvc.perform(post(BASE + "/matters/" + UUID.randomUUID() + "/compute").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(computeBody()))).andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(authorities = "LEGALPRACTICE_READ")
    @DisplayName("POST compute, preview and recompute with only READ return 403; reads return 200")
    void readOnlyCanReadButNotCompute() throws Exception {
        mvc.perform(post(BASE + "/matters/" + UUID.randomUUID() + "/compute").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(computeBody()))).andExpect(status().isForbidden());
        mvc.perform(post(BASE + "/preview").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(computeBody()))).andExpect(status().isForbidden());
        mvc.perform(post(BASE + "/computations/" + UUID.randomUUID() + "/recompute").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"triggerDate\":\"2026-03-09\",\"reason\":\"Served on the 9th, not the 6th\"}")).andExpect(status().isForbidden());
        verify(service, never()).compute(any(), any(), any(), any(), any());
        mvc.perform(get(BASE + "/rules")).andExpect(status().isOk());
        mvc.perform(get(BASE + "/calendar?year=2026")).andExpect(status().isOk());
        mvc.perform(get(BASE + "/matters/" + UUID.randomUUID())).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = "LEGALPRACTICE_DEADLINE_COMPUTE")
    @DisplayName("POST recompute without a reason returns 400")
    void recomputeWithoutReasonReturns400() throws Exception {
        mvc.perform(post(BASE + "/computations/" + UUID.randomUUID() + "/recompute").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"triggerDate\":\"2026-03-09\",\"reason\":\"\"}")).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Without any authority every endpoint is rejected")
    @WithMockUser(authorities = "SOMETHING_ELSE")
    void noAuthorityIsRejected() throws Exception {
        mvc.perform(get(BASE + "/rules")).andExpect(status().isForbidden());
        mvc.perform(post(BASE + "/preview").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(computeBody()))).andExpect(status().isForbidden());
    }
}
