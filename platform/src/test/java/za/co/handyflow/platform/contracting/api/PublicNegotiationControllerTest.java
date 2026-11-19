package za.co.handyflow.platform.contracting.api;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import za.co.handyflow.platform.WebMvcTestSecuritySupport;
import za.co.handyflow.platform.contracting.application.internal.ContractNegotiationAccessService;
import za.co.handyflow.platform.contracting.application.internal.PublicSigningRateLimiter;
import za.co.handyflow.platform.contracting.dto.ContractNegotiationDtos.LinkInfo;
import za.co.handyflow.platform.shared.HandyFlowException;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * The public negotiation endpoints through the real security configuration and error handler, with no login. Counterparties arrive with
 * a link, not an account, so every request here is anonymous and sends no CSRF token.
 */
@WebMvcTest(PublicNegotiationController.class)
@Import(WebMvcTestSecuritySupport.class)
class PublicNegotiationControllerTest {

    static final String BASE = "/api/v1/negotiate/tok123";

    @Autowired MockMvc mvc;
    @MockitoBean ContractNegotiationAccessService access;
    @MockitoBean PublicSigningRateLimiter limiter;

    @Test
    void anAnonymousVisitorReachesTheLinkInfoAndIsRateLimitedBeforeAnyLookup() throws Exception {
        when(access.info("tok123")).thenReturn(new LinkInfo("Supply agreement", "EMAIL", "a***@acme.co", Instant.now().plusSeconds(3600)));
        mvc.perform(get(BASE)).andExpect(status().isOk()).andExpect(jsonPath("$.data.contractTitle").value("Supply agreement"));
        InOrder order = inOrder(limiter, access);
        order.verify(limiter).checkNegotiateIp(any());
        order.verify(limiter).checkNegotiateView(any(), eq("tok123"));
        order.verify(access).info("tok123");
    }

    @Test
    void aRateLimitedRequestNeverReachesTheAccessService() throws Exception {
        doThrow(new HandyFlowException("Too many requests", HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED")).when(limiter).checkNegotiateIp(any());
        mvc.perform(get(BASE)).andExpect(status().isTooManyRequests());
        verifyNoInteractions(access);
    }

    @Test
    void anUnknownLinkIsANotFound() throws Exception {
        when(access.info(anyString())).thenThrow(new HandyFlowException("This link is not valid or has expired.", HttpStatus.NOT_FOUND, "NEGOTIATION_LINK_INVALID"));
        mvc.perform(get(BASE)).andExpect(status().isNotFound());
    }

    @Test
    void theNegotiationNeedsASessionAndAskingWithoutOneIsRefused() throws Exception {
        when(access.authenticate(eq("tok123"), isNull())).thenThrow(new HandyFlowException("Please confirm your code to continue.", HttpStatus.UNAUTHORIZED, "NEGOTIATION_SESSION_REQUIRED"));
        mvc.perform(get(BASE + "/negotiation")).andExpect(status().isUnauthorized());
        verify(access, never()).view(any());
    }

    @Test
    void theSessionHeaderIsPassedThroughToTheAuthenticationCheck() throws Exception {
        when(access.authenticate(eq("tok123"), eq("sess-1"))).thenThrow(new HandyFlowException("x", HttpStatus.UNAUTHORIZED, "NEGOTIATION_SESSION_REQUIRED"));
        mvc.perform(get(BASE + "/negotiation").header("X-Negotiation-Session", "sess-1")).andExpect(status().isUnauthorized());
        verify(access).authenticate("tok123", "sess-1");
    }

    @Test
    void aWrongCodeIsRefusedAndNoSessionIsIssued() throws Exception {
        when(access.verifyCode(eq("tok123"), eq("000000"), any())).thenReturn(null);
        mvc.perform(post(BASE + "/verify").contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"000000\"}")).andExpect(status().isUnauthorized());
    }

    @Test
    void aMalformedCodeIsRejectedBeforeTheServiceSeesIt() throws Exception {
        mvc.perform(post(BASE + "/verify").contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"12\"}")).andExpect(status().isBadRequest());
        verify(access, never()).verifyCode(any(), any(), any());
    }

    @Test
    void aProposalWithoutAKindIsRejected() throws Exception {
        mvc.perform(post(BASE + "/proposals").header("X-Negotiation-Session", "s").contentType(MediaType.APPLICATION_JSON).content("{\"blockIndex\":0}"))
                .andExpect(status().isBadRequest());
        verify(access, never()).propose(any(), any(), any());
    }

    @Test
    void nothingCanBeChangedOrDeletedThroughAPublicLink() throws Exception {
        mvc.perform(put(BASE + "/negotiation").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isMethodNotAllowed());
        mvc.perform(delete(BASE + "/negotiation")).andExpect(status().isMethodNotAllowed());
        mvc.perform(post(BASE + "/apply").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isNotFound());
        // a counterparty answers proposals addressed to them, but staff-only steps do not exist on the public side
        for (String staffOnly : new String[]{"/ready", "/close", "/reopen", "/deadline", "/proposals/" + java.util.UUID.randomUUID() + "/apply"})
            mvc.perform(post(BASE + staffOnly).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isNotFound());
        verifyNoInteractions(access);
    }

    @Test
    void answeringAProposalNeedsASessionAndIsRateLimitedFirst() throws Exception {
        java.util.UUID id = java.util.UUID.randomUUID();
        when(access.authenticate(eq("tok123"), isNull())).thenThrow(new HandyFlowException("Please confirm your code to continue.", HttpStatus.UNAUTHORIZED, "NEGOTIATION_SESSION_REQUIRED"));
        mvc.perform(post(BASE + "/proposals/" + id + "/accept").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isUnauthorized());
        mvc.perform(post(BASE + "/proposals/" + id + "/reject").contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"no\"}")).andExpect(status().isUnauthorized());
        mvc.perform(post(BASE + "/proposals/" + id + "/counter").contentType(MediaType.APPLICATION_JSON).content("{\"proposedHtml\":\"<p>x</p>\"}")).andExpect(status().isUnauthorized());
        verify(limiter, times(3)).checkNegotiateAct(any(), eq("tok123"));
        verify(access, never()).accept(any(), any(), any(), any());
        verify(access, never()).reject(any(), any(), any(), any());
        verify(access, never()).counter(any(), any(), any(), any());
    }

    @Test
    void aRejectionNeedsAReasonAndACounterNeedsWording() throws Exception {
        java.util.UUID id = java.util.UUID.randomUUID();
        mvc.perform(post(BASE + "/proposals/" + id + "/reject").header("X-Negotiation-Session", "s").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isBadRequest());
        mvc.perform(post(BASE + "/proposals/" + id + "/counter").header("X-Negotiation-Session", "s").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isBadRequest());
        verify(access, never()).reject(any(), any(), any(), any());
        verify(access, never()).counter(any(), any(), any(), any());
    }
}
