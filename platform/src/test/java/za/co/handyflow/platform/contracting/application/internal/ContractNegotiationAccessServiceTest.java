package za.co.handyflow.platform.contracting.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import za.co.handyflow.platform.contracting.application.internal.ContractNegotiationAccessService.Access;
import za.co.handyflow.platform.contracting.domain.model.*;
import za.co.handyflow.platform.contracting.domain.repository.*;
import za.co.handyflow.platform.contracting.dto.ContractNegotiationDtos.*;
import za.co.handyflow.platform.shared.EmailService;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.SmsService;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ContractNegotiationAccessServiceTest {

    TenantId tenant = TenantId.generate();
    UUID staff = UUID.randomUUID();
    Contract contract;
    ContractParty withPhone, emailOnly, noContact;
    ContractNegotiation negotiation;
    final List<ContractNegotiationLink> links = new ArrayList<>();
    final List<ContractNegotiationSession> sessions = new ArrayList<>();
    final List<ContractNegotiationEvent> events = new ArrayList<>();
    ContractNegotiationService negotiationService = mock(ContractNegotiationService.class);
    OtpService otp = mock(OtpService.class);
    PublicSigningRateLimiter limiter = mock(PublicSigningRateLimiter.class);
    EmailService email = mock(EmailService.class);
    SmsService sms = mock(SmsService.class);
    ContractNegotiationAccessService service;

    @BeforeEach
    void setUp() {
        contract = Contract.create(tenant, "CTR-1", "Service agreement", "SERVICE", "<p>Terms</p>", null, staff);
        UUID cid = contract.getId(), tid = tenant.getValue();
        withPhone = ContractParty.create(tenant, cid, "COMPANY", "CLIENT", "Acme", "a@acme.co", "0821234567", "Acme", 1);
        emailOnly = ContractParty.create(tenant, cid, "COMPANY", "SUPPLIER", "Beta", "beta@beta.co", null, "Beta", 2);
        noContact = ContractParty.create(tenant, cid, "COMPANY", "OTHER", "Gamma", null, null, "Gamma", 3);
        negotiation = ContractNegotiation.open(tid, cid, 1, LocalDate.now().plusDays(30), staff, LocalDate.now());

        ContractRepository contractRepo = mock(ContractRepository.class);
        when(contractRepo.findActiveById(tenant, cid)).thenReturn(Optional.of(contract));
        ContractPartyRepository partyRepo = mock(ContractPartyRepository.class);
        when(partyRepo.findByContract(cid)).thenReturn(List.of(withPhone, emailOnly, noContact));
        ContractNegotiationRepository nRepo = mock(ContractNegotiationRepository.class);
        when(nRepo.findByTenantIdAndContractId(tid, cid)).thenAnswer(i -> Optional.of(negotiation));
        when(nRepo.findById(negotiation.getId())).thenAnswer(i -> Optional.of(negotiation));

        ContractNegotiationLinkRepository lRepo = mock(ContractNegotiationLinkRepository.class);
        when(lRepo.save(any())).thenAnswer(i -> { ContractNegotiationLink l = i.getArgument(0); if (!links.contains(l)) links.add(l); return l; });
        when(lRepo.findByTokenHash(anyString())).thenAnswer(i -> links.stream().filter(l -> l.getTokenHash().equals(i.getArgument(0))).findFirst());
        when(lRepo.findByTenantIdAndId(eq(tid), any())).thenAnswer(i -> links.stream().filter(l -> l.getId().equals(i.getArgument(1))).findFirst());
        when(lRepo.findByTenantIdAndContractIdAndPartyIdAndRevokedAtIsNull(eq(tid), eq(cid), any()))
                .thenAnswer(i -> links.stream().filter(l -> l.getPartyId().equals(i.getArgument(2)) && l.getRevokedAt() == null).toList());
        when(lRepo.findByTenantIdAndContractIdOrderByCreatedAtDesc(tid, cid)).thenAnswer(i -> new ArrayList<>(links));
        ContractNegotiationSessionRepository sRepo = mock(ContractNegotiationSessionRepository.class);
        when(sRepo.save(any())).thenAnswer(i -> { ContractNegotiationSession s = i.getArgument(0); sessions.add(s); return s; });
        when(sRepo.findBySessionHash(anyString())).thenAnswer(i -> sessions.stream().filter(s -> s.getSessionHash().equals(i.getArgument(0))).findFirst());
        ContractNegotiationEventRepository eRepo = mock(ContractNegotiationEventRepository.class);
        when(eRepo.save(any())).thenAnswer(i -> { events.add(i.getArgument(0)); return i.getArgument(0); });

        when(otp.generateAndStore(anyString())).thenReturn("123456");
        when(otp.verify(anyString(), anyString())).thenAnswer(i -> "123456".equals(i.getArgument(1)));
        when(sms.send(anyString(), anyString())).thenReturn(true);

        service = new ContractNegotiationAccessService(contractRepo, partyRepo, nRepo, lRepo, sRepo, eRepo, negotiationService, otp, limiter, email, sms);
        ReflectionTestUtils.setField(service, "baseUrl", "https://app.test");
        ReflectionTestUtils.setField(service, "linkValidityDays", 14);
        ReflectionTestUtils.setField(service, "sessionMinutes", 60);
    }

    String tokenOf(IssuedLinkView v) { return v.url().substring(v.url().lastIndexOf('/') + 1); }

    IssuedLinkView issue(ContractParty p) { return service.issueLink(tenant, contract.getId(), p.getId(), staff); }

    // ── issuing ──────────────────────────────────────────────────────────────

    @Test
    void onlyTheHashOfTheTokenIsStored() {
        IssuedLinkView v = issue(withPhone);
        String token = tokenOf(v);
        assertTrue(v.url().startsWith("https://app.test/negotiate/"));
        assertTrue(token.length() >= 40, "256 random bits");
        ContractNegotiationLink l = links.get(0);
        assertEquals(ContractNegotiationAccessService.sha256(token), l.getTokenHash());
        assertNotEquals(token, l.getTokenHash());
        assertFalse(events.stream().anyMatch(e -> e.getDetail() != null && e.getDetail().contains(token)), "the token is never audited");
    }

    @Test
    void issuingSendsTheLinkByEmailWhenThereIsOne() {
        issue(withPhone);
        verify(email).send(eq("a@acme.co"), anyString(), contains("/negotiate/"));
        assertEquals("QUEUED", links.get(0).getDeliveryStatus());
    }

    @Test
    void aNewLinkRevokesThePartysEarlierLink() {
        IssuedLinkView first = issue(withPhone);
        IssuedLinkView second = issue(withPhone);
        assertNotNull(links.get(0).getRevokedAt());
        assertNull(links.get(1).getRevokedAt());
        assertThrows(HandyFlowException.class, () -> service.info(tokenOf(first)));
        assertDoesNotThrow(() -> service.info(tokenOf(second)));
    }

    @Test
    void aLinkNeverOutlivesTheResponseDeadline() {
        negotiation.setDeadline(LocalDate.now().plusDays(2), LocalDate.now());
        IssuedLinkView v = issue(withPhone);
        assertTrue(v.expiresAt().isBefore(Instant.now().plusSeconds(4L * 24 * 3600)));
    }

    @Test
    void cannotIssueWithoutContactDetailsOrForAnotherContractsPartyOrAfterClose() {
        assertThrows(IllegalStateException.class, () -> issue(noContact));
        assertThrows(za.co.handyflow.platform.shared.ResourceNotFoundException.class, () -> service.issueLink(tenant, contract.getId(), UUID.randomUUID(), staff));
        negotiation.close("done", staff);
        assertThrows(IllegalStateException.class, () -> issue(withPhone));
    }

    @Test
    void staffListNeverContainsTheTokenOrItsHash() {
        issue(withPhone);
        List<LinkView> list = service.links(tenant, contract.getId());
        assertEquals(1, list.size());
        assertTrue(list.get(0).active());
        assertEquals("Acme", list.get(0).partyName());
        assertFalse(list.get(0).toString().contains(links.get(0).getTokenHash()));
    }

    // ── identity check ───────────────────────────────────────────────────────

    @Test
    void infoShowsOnlyTheTitleAndAMaskedDestination() {
        LinkInfo info = service.info(tokenOf(issue(withPhone)));
        assertEquals("Service agreement", info.contractTitle());
        assertEquals("SMS", info.channel());
        assertEquals("******4567", info.maskedDestination());
        LinkInfo viaEmail = service.info(tokenOf(issue(emailOnly)));
        assertEquals("EMAIL", viaEmail.channel());
        assertEquals("b****@beta.co", viaEmail.maskedDestination());
    }

    @Test
    void codeGoesBySmsWhenThereIsAPhoneOtherwiseByEmail() {
        service.requestCode(tokenOf(issue(withPhone)), "1.1.1.1");
        verify(sms).send(eq("0821234567"), contains("123456"));
        service.requestCode(tokenOf(issue(emailOnly)), "1.1.1.1");
        verify(email).send(eq("beta@beta.co"), anyString(), contains("123456"));
    }

    @Test
    void theCodeIsKeyedToTheLinkNotToThePartyAndIsRateLimited() {
        IssuedLinkView v = issue(withPhone);
        service.requestCode(tokenOf(v), "1.1.1.1");
        verify(otp).generateAndStore(v.linkId().toString());
        verify(otp, never()).generateAndStore(withPhone.getId().toString());
        verify(limiter).checkNegotiateOtpRequest(v.linkId());
    }

    @Test
    void aRateLimitedCodeRequestSendsNothing() {
        IssuedLinkView v = issue(withPhone);
        doThrow(new HandyFlowException("slow down", org.springframework.http.HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED")).when(limiter).checkNegotiateOtpRequest(any());
        assertThrows(HandyFlowException.class, () -> service.requestCode(tokenOf(v), "1.1.1.1"));
        verify(sms, never()).send(anyString(), anyString());
        verify(otp, never()).generateAndStore(anyString());
    }

    @Test
    void aWrongCodeGivesNoSessionAndIsAudited() {
        IssuedLinkView v = issue(withPhone);
        assertNull(service.verifyCode(tokenOf(v), "000000", "1.1.1.1"));
        assertTrue(sessions.isEmpty());
        assertTrue(events.stream().anyMatch(e -> e.getEventType().equals("CODE_FAILED") && "1.1.1.1".equals(e.getIpAddress())));
        verify(limiter).checkOtpVerify(v.linkId());
    }

    @Test
    void aCorrectCodeGivesASessionStoredOnlyAsAHash() {
        IssuedLinkView v = issue(withPhone);
        SessionView s = service.verifyCode(tokenOf(v), "123456", "1.1.1.1");
        assertNotNull(s.sessionToken());
        assertEquals(ContractNegotiationAccessService.sha256(s.sessionToken()), sessions.get(0).getSessionHash());
        assertTrue(s.expiresAt().isBefore(Instant.now().plusSeconds(61 * 60)));
        assertTrue(events.stream().anyMatch(e -> e.getEventType().equals("ACCESS_VERIFIED")));
    }

    // ── every later request ──────────────────────────────────────────────────

    @Test
    void dataRequestsNeedBothTheLinkAndASession() {
        IssuedLinkView v = issue(withPhone);
        String token = tokenOf(v);
        assertEquals("NEGOTIATION_SESSION_REQUIRED", assertThrows(HandyFlowException.class, () -> service.authenticate(token, null)).getErrorCode());
        assertEquals("NEGOTIATION_SESSION_REQUIRED", assertThrows(HandyFlowException.class, () -> service.authenticate(token, "made-up")).getErrorCode());
        String session = service.verifyCode(token, "123456", "1.1.1.1").sessionToken();
        Access a = service.authenticate(token, session);
        assertEquals(tenant, a.tenantId());
        assertEquals(contract.getId(), a.contractId());
        assertEquals(withPhone.getId(), a.partyId());
    }

    @Test
    void aSessionIsUselessWithAnotherPartysLink() {
        String sessionA = service.verifyCode(tokenOf(issue(withPhone)), "123456", "1.1.1.1").sessionToken();
        String tokenB = tokenOf(issue(emailOnly));
        assertEquals("NEGOTIATION_SESSION_REQUIRED", assertThrows(HandyFlowException.class, () -> service.authenticate(tokenB, sessionA)).getErrorCode());
    }

    @Test
    void anExpiredSessionIsRefused() {
        String token = tokenOf(issue(withPhone));
        String session = service.verifyCode(token, "123456", "1.1.1.1").sessionToken();
        ReflectionTestUtils.setField(sessions.get(0), "expiresAt", Instant.now().minusSeconds(1));
        assertThrows(HandyFlowException.class, () -> service.authenticate(token, session));
    }

    @Test
    void revokingTheLinkKillsAnExistingSession() {
        IssuedLinkView v = issue(withPhone);
        String token = tokenOf(v);
        String session = service.verifyCode(token, "123456", "1.1.1.1").sessionToken();
        service.revokeLink(tenant, contract.getId(), v.linkId(), staff);
        assertEquals("NEGOTIATION_LINK_INVALID", assertThrows(HandyFlowException.class, () -> service.authenticate(token, session)).getErrorCode());
    }

    @Test
    void everyKindOfBadLinkLooksTheSame() {
        IssuedLinkView expired = issue(withPhone);
        ReflectionTestUtils.setField(links.get(0), "expiresAt", Instant.now().minusSeconds(1));
        IssuedLinkView revoked = issue(emailOnly);
        service.revokeLink(tenant, contract.getId(), revoked.linkId(), staff);
        List<HandyFlowException> errors = new ArrayList<>();
        for (String t : new String[]{tokenOf(expired), tokenOf(revoked), "totally-unknown-token-value-123456", "short", null})
            errors.add(assertThrows(HandyFlowException.class, () -> service.info(t)));
        assertTrue(errors.stream().allMatch(e -> e.getErrorCode().equals("NEGOTIATION_LINK_INVALID") && e.getMessage().equals(errors.get(0).getMessage())));
    }

    @Test
    void aLinkStopsWorkingWhenTheNegotiationEndsOrTheContractIsSent() {
        String token = tokenOf(issue(withPhone));
        assertDoesNotThrow(() -> service.info(token));
        negotiation.markReady(0, 0, 1, "h", staff);
        assertThrows(HandyFlowException.class, () -> service.info(token));
        negotiation.reopen();
        assertDoesNotThrow(() -> service.info(token), "links are revoked by the negotiation service; the entity alone does not do it");
        contract.send();
        assertThrows(HandyFlowException.class, () -> service.info(token));
    }

    @Test
    void identityComesFromTheLinkNeverFromTheCaller() {
        // authenticate() takes no tenant, contract or party argument at all; the Access it returns is built from the stored link
        IssuedLinkView v = issue(emailOnly);
        String token = tokenOf(v);
        String session = service.verifyCode(token, "123456", "9.9.9.9").sessionToken();
        Access a = service.authenticate(token, session);
        assertEquals(emailOnly.getId(), a.partyId());
        assertEquals(v.linkId(), a.linkId());
    }

    @Test
    void counterpartyActionsAreDelegatedWithTheLinksIdentity() {
        IssuedLinkView v = issue(withPhone);
        Access a = new Access(tenant, contract.getId(), withPhone.getId(), v.linkId());
        ProposalInput in = new ProposalInput("DELETE", 0, "<p>Terms</p>", null, "why");
        service.propose(a, in, "2.2.2.2");
        verify(negotiationService).submitProposal(tenant, contract.getId(), withPhone.getId(), in, "2.2.2.2");
        UUID pid = UUID.randomUUID();
        service.withdraw(a, pid, "2.2.2.2");
        verify(negotiationService).withdraw(tenant, contract.getId(), withPhone.getId(), pid, "2.2.2.2");
        ArgumentCaptor<CounterpartyCommentInput> c = ArgumentCaptor.forClass(CounterpartyCommentInput.class);
        service.comment(a, new CounterpartyCommentInput("hi", null, null, null, null), "2.2.2.2");
        verify(negotiationService).counterpartyComment(eq(tenant), eq(contract.getId()), eq(withPhone.getId()), c.capture(), eq("2.2.2.2"));
        assertEquals("hi", c.getValue().body());
    }
}
