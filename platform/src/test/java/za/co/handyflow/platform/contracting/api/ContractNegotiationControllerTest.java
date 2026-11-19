package za.co.handyflow.platform.contracting.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import za.co.handyflow.platform.WebMvcTestSecuritySupport;
import za.co.handyflow.platform.contracting.application.internal.ContractNegotiationAccessService;
import za.co.handyflow.platform.contracting.application.internal.ContractNegotiationService;

import java.util.UUID;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Who may do what to a contract's negotiation, through the real method security. The service is mocked: only the guard is under test. */
@WebMvcTest(ContractNegotiationController.class)
@Import(WebMvcTestSecuritySupport.class)
class ContractNegotiationControllerTest {

    static final String BASE = "/api/v1/contracts/" + UUID.randomUUID() + "/negotiation";
    static final String PROPOSAL = BASE + "/proposals/" + UUID.randomUUID();

    @Autowired MockMvc mvc;
    @MockitoBean ContractNegotiationService service;
    @MockitoBean ContractNegotiationAccessService access;

    @Test
    void anonymousIsRefused() throws Exception {
        mvc.perform(get(BASE)).andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test
    @WithMockUser(authorities = "SOMETHING_ELSE")
    void aUserWithoutAContractsPermissionIsForbiddenEverywhere() throws Exception {
        mvc.perform(get(BASE)).andExpect(status().isForbidden());
        mvc.perform(get(BASE + "/links")).andExpect(status().isForbidden());
        mvc.perform(post(BASE + "/open").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());
        verifyNoInteractions(service, access);
    }

    @Test
    @WithMockUser(authorities = "CONTRACTS_READ")
    void aReaderCanLookAtTheNegotiationAndItsLinks() throws Exception {
        mvc.perform(get(BASE)).andExpect(status().isOk());
        mvc.perform(get(BASE + "/links")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = "CONTRACTS_READ")
    void aReaderCannotChangeAnything() throws Exception {
        for (String url : new String[]{BASE + "/open", BASE + "/proposals", PROPOSAL + "/counter", PROPOSAL + "/withdraw", PROPOSAL + "/accept", PROPOSAL + "/reject", BASE + "/apply", BASE + "/comments",
                BASE + "/ready", BASE + "/close", BASE + "/reopen", BASE + "/links", BASE + "/links/" + UUID.randomUUID() + "/revoke"}) {
            mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());
        }
        mvc.perform(put(BASE + "/deadline").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());
        verifyNoInteractions(service, access);
    }

    @Test
    @WithMockUser(authorities = "CONTRACTS_TEMPLATES")
    void theTemplatesPermissionAloneDoesNotReachNegotiation() throws Exception {
        mvc.perform(get(BASE)).andExpect(status().isForbidden());
        mvc.perform(post(BASE + "/open").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "CONTRACTS_MANAGE")
    void aManagerCanRunTheNegotiation() throws Exception {
        mvc.perform(post(BASE + "/open").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isOk());
        mvc.perform(post(BASE + "/ready")).andExpect(status().isOk());
        mvc.perform(post(PROPOSAL + "/accept").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isOk());
        verify(service).open(any(), any(), any(), any());
    }

    @Test
    @WithMockUser(authorities = "CONTRACTS_MANAGE")
    void rejectingNeedsAReason() throws Exception {
        mvc.perform(post(PROPOSAL + "/reject").contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"\"}")).andExpect(status().isBadRequest());
        verify(service, never()).reject(any(), any(), any(), any(), any());
    }

    @Test
    @WithMockUser(authorities = "CONTRACTS_ADMIN")
    void anAdministratorCanAlsoManageLinks() throws Exception {
        mvc.perform(post(BASE + "/links/" + UUID.randomUUID() + "/revoke")).andExpect(status().isOk());
    }
}
