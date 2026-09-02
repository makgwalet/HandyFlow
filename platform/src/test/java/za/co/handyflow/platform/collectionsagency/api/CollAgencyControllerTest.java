package za.co.handyflow.platform.collectionsagency.api;

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
import za.co.handyflow.platform.collectionsagency.application.internal.CollAgencyClientService;
import za.co.handyflow.platform.collectionsagency.application.internal.CollAgencyProfileService;
import za.co.handyflow.platform.collectionsagency.domain.model.CollAgencyClient;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CollAgencyController.class)
@Import(WebMvcTestSecuritySupport.class)
class CollAgencyControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    @MockitoBean CollAgencyProfileService profileService;
    @MockitoBean CollAgencyClientService clientService;
    @MockitoBean FeatureGuard featureGuard;

    static final String BASE = "/api/v1/collections-agency";

    private CollAgencyClient testClient() {
        return CollAgencyClient.create(UUID.randomUUID(), "Acme Retailers", "REG123", new BigDecimal("20.00"),
                "Jane", "jane@acme.co.za", "0821234567", "1 Main St");
    }

    @Test
    @WithMockUser(authorities = "COLLECTIONSAGENCY_READ")
    @DisplayName("GET /clients returns 200 with COLLECTIONSAGENCY_READ")
    void listClientsReturns200() throws Exception {
        when(clientService.list(any(), any())).thenReturn(new PageImpl<>(List.of(testClient())));

        mvc.perform(get(BASE + "/clients"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.content[0].tradingName").value("Acme Retailers"));
    }

    @Test
    @WithMockUser(authorities = "SOME_OTHER_AUTHORITY")
    @DisplayName("GET /clients returns 403 without any COLLECTIONSAGENCY_* authority")
    void listClientsReturns403WithoutAuthority() throws Exception {
        mvc.perform(get(BASE + "/clients")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "COLLECTIONSAGENCY_READ")
    @DisplayName("POST /clients with only COLLECTIONSAGENCY_READ returns 403")
    void createClientWithReadOnlyReturns403() throws Exception {
        mvc.perform(post(BASE + "/clients").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"tradingName":"Acme Retailers"}
                            """))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "COLLECTIONSAGENCY_MANAGE")
    @DisplayName("POST /clients with COLLECTIONSAGENCY_MANAGE returns 201")
    void createClientWithManageReturns201() throws Exception {
        when(clientService.create(any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(testClient());

        mvc.perform(post(BASE + "/clients").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"tradingName":"Acme Retailers"}
                            """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.tradingName").value("Acme Retailers"));
    }

    @Test
    @WithMockUser(authorities = "COLLECTIONSAGENCY_MANAGE")
    @DisplayName("DELETE /clients/{id} with only COLLECTIONSAGENCY_MANAGE returns 403 — ADMIN only")
    void deleteClientWithManageOnlyReturns403() throws Exception {
        mvc.perform(delete(BASE + "/clients/" + UUID.randomUUID()).with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "COLLECTIONSAGENCY_ADMIN")
    @DisplayName("DELETE /clients/{id} with COLLECTIONSAGENCY_ADMIN returns 200")
    void deleteClientWithAdminReturns200() throws Exception {
        mvc.perform(delete(BASE + "/clients/" + UUID.randomUUID()).with(csrf()))
                .andExpect(status().isOk());
    }
}
