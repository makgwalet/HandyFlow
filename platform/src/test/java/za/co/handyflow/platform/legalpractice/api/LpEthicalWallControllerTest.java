package za.co.handyflow.platform.legalpractice.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
import za.co.handyflow.platform.legalpractice.application.internal.LpEthicalWallService;
import za.co.handyflow.platform.legalpractice.dto.WallResponse;
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

/** Who may put walls up, lift them, see them, and see who was turned away. What a wall does is covered in {@code LpEthicalWallServiceTest}. */
@WebMvcTest(LpEthicalWallController.class)
@Import(WebMvcTestSecuritySupport.class)
class LpEthicalWallControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    @MockitoBean LpEthicalWallService service;
    @MockitoBean FeatureGuard featureGuard;

    static final String BASE = "/api/v1/legal-practice/ethical-walls";

    @BeforeEach
    void seedTenantContext() {
        TenantContext.setTenantId(UUID.randomUUID().toString());
        TenantContext.setUserId(UUID.randomUUID().toString());
        TenantContext.setUserName("Admin Person");
    }

    @AfterEach
    void clearTenantContext() { TenantContext.clear(); }

    WallResponse wall() {
        return new WallResponse(UUID.randomUUID(), "MATTER", UUID.randomUUID(), null, "M-1 Matter", UUID.randomUUID(), "Barred Person",
                "Acted for the other side", "Admin Person", Instant.now(), true, null, null, null);
    }

    Map<String, Object> body() {
        return Map.of("scope", "MATTER", "matterId", UUID.randomUUID().toString(), "userId", UUID.randomUUID().toString(),
                "userName", "Barred Person", "reason", "Acted for the other side before joining");
    }

    @Test
    @WithMockUser(authorities = "LEGALPRACTICE_WALL_MANAGE")
    @DisplayName("POST ethical-walls with LEGALPRACTICE_WALL_MANAGE returns 201")
    void createWithManageReturns201() throws Exception {
        when(service.create(any(), any(), any(), any())).thenReturn(wall());
        mvc.perform(post(BASE).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body())))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(authorities = {"LEGALPRACTICE_MANAGE", "LEGALPRACTICE_READ", "LEGALPRACTICE_WALL_AUDIT"})
    @DisplayName("POST ethical-walls without WALL_MANAGE returns 403 — managing a matter or auditing walls is not putting one up")
    void createWithoutManageReturns403() throws Exception {
        mvc.perform(post(BASE).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body())))
                .andExpect(status().isForbidden());
        verify(service, never()).create(any(), any(), any(), any());
    }

    @Test
    @WithMockUser(authorities = "LEGALPRACTICE_WALL_MANAGE")
    @DisplayName("POST ethical-walls without a person or reason returns 400")
    void createWithoutPersonOrReasonReturns400() throws Exception {
        mvc.perform(post(BASE).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"scope\":\"MATTER\",\"reason\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(authorities = "LEGALPRACTICE_WALL_MANAGE")
    @DisplayName("POST lift with LEGALPRACTICE_WALL_MANAGE returns 200")
    void liftWithManageReturns200() throws Exception {
        when(service.lift(any(), any(), any(), any(), any())).thenReturn(wall());
        mvc.perform(post(BASE + "/" + UUID.randomUUID() + "/lift").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"The other side has consented in writing\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = {"LEGALPRACTICE_WALL_AUDIT", "LEGALPRACTICE_MANAGE"})
    @DisplayName("POST lift without WALL_MANAGE returns 403")
    void liftWithoutManageReturns403() throws Exception {
        mvc.perform(post(BASE + "/" + UUID.randomUUID() + "/lift").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"The other side has consented in writing\"}"))
                .andExpect(status().isForbidden());
        verify(service, never()).lift(any(), any(), any(), any(), any());
    }

    @Test
    @WithMockUser(authorities = "LEGALPRACTICE_WALL_MANAGE")
    @DisplayName("POST lift without a reason returns 400")
    void liftWithoutReasonReturns400() throws Exception {
        mvc.perform(post(BASE + "/" + UUID.randomUUID() + "/lift").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(authorities = "LEGALPRACTICE_WALL_MANAGE")
    @DisplayName("GET ethical-walls with WALL_MANAGE returns 200")
    void listWithManageReturns200() throws Exception {
        when(service.list(any(), org.mockito.ArgumentMatchers.anyBoolean(), any())).thenReturn(new PageImpl<>(List.of(wall())));
        mvc.perform(get(BASE)).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = {"LEGALPRACTICE_READ", "LEGALPRACTICE_MANAGE"})
    @DisplayName("GET ethical-walls with ordinary matter permissions returns 403 — who is walled off is not for every reader")
    void listWithoutWallPermissionReturns403() throws Exception {
        mvc.perform(get(BASE)).andExpect(status().isForbidden());
        mvc.perform(get(BASE + "/denials")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "LEGALPRACTICE_WALL_AUDIT")
    @DisplayName("GET denials with WALL_AUDIT returns 200")
    void denialsWithAuditReturns200() throws Exception {
        when(service.denials(any(), any())).thenReturn(new PageImpl<>(List.of()));
        mvc.perform(get(BASE + "/denials")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = "LEGALPRACTICE_WALL_MANAGE")
    @DisplayName("GET denials with only WALL_MANAGE returns 403 — putting walls up is not reading the log")
    void denialsWithManageOnlyReturns403() throws Exception {
        mvc.perform(get(BASE + "/denials")).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET ethical-walls with no authentication is rejected")
    void unauthenticatedIsRejected() throws Exception {
        mvc.perform(get(BASE)).andExpect(result ->
                org.assertj.core.api.Assertions.assertThat(result.getResponse().getStatus()).isIn(401, 403));
    }
}
