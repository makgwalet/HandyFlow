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
import za.co.handyflow.platform.contracting.application.internal.ContractApprovalService;
import za.co.handyflow.platform.contracting.domain.model.ApprovalAction;
import za.co.handyflow.platform.contracting.domain.model.ContractApproval;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Who may set the approval policy, ask for approval and decide, through the real method security. The service is mocked. */
@WebMvcTest(ContractApprovalController.class)
@Import(WebMvcTestSecuritySupport.class)
class ContractApprovalControllerTest {

    static final String BASE = "/api/v1/contracts/approvals";
    static final String JSON_REQUEST = "{\"action\":\"%s\",\"subjectId\":\"" + UUID.randomUUID() + "\"}";

    @Autowired MockMvc mvc;
    @MockitoBean ContractApprovalService service;

    private ContractApproval someone(ApprovalAction a) {
        return ContractApproval.request(UUID.randomUUID(), a, UUID.randomUUID(), "x", "h", UUID.randomUUID(), null);
    }

    @Test
    void anonymousIsRefused() throws Exception {
        mvc.perform(get(BASE)).andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test
    @WithMockUser(authorities = "SOMETHING_ELSE")
    void withoutAContractsPermissionEverythingIsForbidden() throws Exception {
        mvc.perform(get(BASE)).andExpect(status().isForbidden());
        mvc.perform(get(BASE + "/policy")).andExpect(status().isForbidden());
        mvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content(JSON_REQUEST.formatted("SEND_FOR_SIGNING"))).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    @WithMockUser(authorities = "CONTRACTS_READ")
    void aReaderCanLookButNotAskOrDecide() throws Exception {
        when(service.policies(any())).thenReturn(List.of());
        when(service.list(any(), any())).thenReturn(List.of());
        mvc.perform(get(BASE + "/policy")).andExpect(status().isOk());
        mvc.perform(get(BASE)).andExpect(status().isOk());
        UUID id = UUID.randomUUID();
        mvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content(JSON_REQUEST.formatted("SEND_FOR_SIGNING"))).andExpect(status().isForbidden());
        for (String verb : new String[]{"approve", "reject"})
            mvc.perform(post(BASE + "/" + id + "/" + verb).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());
        mvc.perform(post(BASE + "/" + id + "/cancel")).andExpect(status().isForbidden());
        verify(service, never()).request(any(), any(), any(), any(), any());
        verify(service, never()).approve(any(), any(), any(), any(), any(), anyBoolean(), any());
    }

    @Test
    @WithMockUser(authorities = "CONTRACTS_MANAGE")
    void onlyAnAdministratorCanChangeThePolicy() throws Exception {
        mvc.perform(put(BASE + "/policy/SEND_FOR_SIGNING").contentType(MediaType.APPLICATION_JSON)
                .content("{\"required\":false,\"allowSoleUserException\":true}")).andExpect(status().isForbidden());
        verify(service, never()).setPolicy(any(), any(), anyBoolean(), anyBoolean(), any());
    }

    @Test
    @WithMockUser(authorities = "CONTRACTS_ADMIN")
    void anAdministratorCanChangeThePolicy() throws Exception {
        when(service.setPolicy(any(), eq(ApprovalAction.TERMINATE), eq(true), eq(false), any()))
                .thenReturn(new ContractApprovalService.EffectivePolicy(ApprovalAction.TERMINATE, true, false, false));
        mvc.perform(put(BASE + "/policy/TERMINATE").contentType(MediaType.APPLICATION_JSON)
                .content("{\"required\":true,\"allowSoleUserException\":false}")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = "CONTRACTS_TEMPLATES")
    void aTemplateAuthorCanAskForClauseApprovalButNotContractApproval() throws Exception {
        when(service.request(any(), eq(ApprovalAction.PUBLISH_CLAUSE), any(), any(), any())).thenReturn(someone(ApprovalAction.PUBLISH_CLAUSE));
        mvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content(JSON_REQUEST.formatted("PUBLISH_CLAUSE"))).andExpect(status().isOk());
        mvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content(JSON_REQUEST.formatted("SEND_FOR_SIGNING"))).andExpect(status().isForbidden());
        verify(service, times(1)).request(any(), any(), any(), any(), any());
    }

    @Test
    @WithMockUser(authorities = "CONTRACTS_MANAGE")
    void aManagerCanAskForAndDecideContractApprovals() throws Exception {
        ContractApproval a = someone(ApprovalAction.SEND_FOR_SIGNING);
        when(service.request(any(), eq(ApprovalAction.SEND_FOR_SIGNING), any(), any(), any())).thenReturn(a);
        when(service.approve(any(), eq(a.getId()), any(), any(), any(), eq(false), any())).thenReturn(a);
        mvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content(JSON_REQUEST.formatted("SEND_FOR_SIGNING"))).andExpect(status().isOk());
        mvc.perform(post(BASE + "/" + a.getId() + "/approve").contentType(MediaType.APPLICATION_JSON).content("{\"note\":\"ok\"}")).andExpect(status().isOk());
        verify(service).approve(any(), eq(a.getId()), any(), argThat(roles -> roles.contains("CONTRACTS_MANAGE")), eq("ok"), eq(false), any());
    }

    @Test
    @WithMockUser(authorities = "CONTRACTS_MANAGE")
    void anUnknownActionIsABadRequest() throws Exception {
        mvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content(JSON_REQUEST.formatted("DELETE_EVERYTHING"))).andExpect(status().is4xxClientError());
        verify(service, never()).request(any(), any(), any(), any(), any());
    }
}
