package za.co.handyflow.platform.contracting.application.internal;

import za.co.handyflow.platform.contracting.domain.model.*;
import za.co.handyflow.platform.contracting.domain.repository.*;
import za.co.handyflow.platform.contracting.dto.ContractRedlineDtos.VersionInfo;
import za.co.handyflow.platform.shared.EmailService;
import za.co.handyflow.platform.shared.SmsService;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * In-memory stand-ins for every negotiation repository, shared by several tenants and contracts, so tests can check isolation
 * and ordering without a database. Every lookup filters on tenant and contract exactly as the real queries do.
 * <p>
 * {@link #inTx} models a database transaction: the row lock taken by {@code contractRepo.findForUpdate} is held until the call returns,
 * as a real {@code SELECT ... FOR UPDATE} would be, so racing threads are serialised the way the database would serialise them.
 */
final class NegotiationHarness {

    final List<Contract> contracts = new CopyOnWriteArrayList<>();
    final List<ContractParty> parties = new CopyOnWriteArrayList<>();
    final List<ContractNegotiation> negotiations = new CopyOnWriteArrayList<>();
    final List<ContractNegotiationProposal> proposals = new CopyOnWriteArrayList<>();
    final List<ContractNegotiationComment> comments = new CopyOnWriteArrayList<>();
    final List<ContractNegotiationEvent> events = new CopyOnWriteArrayList<>();
    final List<ContractNegotiationLink> links = new CopyOnWriteArrayList<>();
    final List<ContractNegotiationSession> sessions = new CopyOnWriteArrayList<>();
    final Map<UUID, AtomicInteger> versions = new ConcurrentHashMap<>();
    final Map<UUID, ReentrantLock> rowLocks = new ConcurrentHashMap<>();

    final ContractRepository contractRepo = mock(ContractRepository.class);
    final ContractPartyRepository partyRepo = mock(ContractPartyRepository.class);
    final ContractNegotiationRepository negotiationRepo = mock(ContractNegotiationRepository.class);
    final ContractNegotiationProposalRepository proposalRepo = mock(ContractNegotiationProposalRepository.class);
    final ContractNegotiationCommentRepository commentRepo = mock(ContractNegotiationCommentRepository.class);
    final ContractNegotiationEventRepository eventRepo = mock(ContractNegotiationEventRepository.class);
    final ContractNegotiationLinkRepository linkRepo = mock(ContractNegotiationLinkRepository.class);
    final ContractNegotiationSessionRepository sessionRepo = mock(ContractNegotiationSessionRepository.class);
    final ContractRedlineService redline = mock(ContractRedlineService.class);
    final OtpService otp = mock(OtpService.class);
    final PublicSigningRateLimiter limiter = mock(PublicSigningRateLimiter.class);
    final EmailService email = mock(EmailService.class);
    final SmsService sms = mock(SmsService.class);

    final org.springframework.context.ApplicationEventPublisher publisher = mock(org.springframework.context.ApplicationEventPublisher.class);
    final ContractApprovalService approvals = mock(ContractApprovalService.class);
    final SigningTokenService hashing = new SigningTokenService(mock(ContractSigningTokenRepository.class));
    final ContractNegotiationService service;
    final ContractNegotiationAccessService access;

    NegotiationHarness() {
        when(contractRepo.findForUpdate(any(), any())).thenAnswer(i -> {
            UUID tenant = i.getArgument(0), id = i.getArgument(1);
            Optional<Contract> c = contracts.stream().filter(x -> x.getTenantId().equals(tenant) && x.getId().equals(id)).findFirst();
            c.ifPresent(x -> {
                ReentrantLock lock = rowLocks.computeIfAbsent(x.getId(), k -> new ReentrantLock());
                if (!lock.isHeldByCurrentThread()) lock.lock();
            });
            return c;
        });
        when(contractRepo.findActiveById(any(), any())).thenAnswer(i -> {
            TenantId tenant = i.getArgument(0);
            UUID id = i.getArgument(1);
            return contracts.stream().filter(x -> x.getTenantId().equals(tenant.getValue()) && x.getId().equals(id)).findFirst();
        });
        when(contractRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        when(partyRepo.findByContract(any())).thenAnswer(i -> parties.stream().filter(p -> p.getContractId().equals(i.<UUID>getArgument(0))).toList());

        when(negotiationRepo.save(any())).thenAnswer(i -> { ContractNegotiation n = i.getArgument(0); if (!negotiations.contains(n)) negotiations.add(n); return n; });
        when(negotiationRepo.findByTenantIdAndContractId(any(), any())).thenAnswer(i -> negotiationOf(i.getArgument(0), i.getArgument(1)));
        when(negotiationRepo.findForUpdate(any(), any())).thenAnswer(i -> negotiationOf(i.getArgument(0), i.getArgument(1)));
        when(negotiationRepo.findById(any())).thenAnswer(i -> negotiations.stream().filter(n -> n.getId().equals(i.<UUID>getArgument(0))).findFirst());

        when(proposalRepo.save(any())).thenAnswer(i -> { ContractNegotiationProposal p = i.getArgument(0); if (!proposals.contains(p)) proposals.add(p); return p; });
        when(proposalRepo.findByTenantIdAndId(any(), any())).thenAnswer(i -> proposals.stream()
                .filter(p -> p.getTenantId().equals(i.<UUID>getArgument(0)) && p.getId().equals(i.<UUID>getArgument(1))).findFirst());
        when(proposalRepo.findByTenantIdAndContractIdOrderByCreatedAtAsc(any(), any())).thenAnswer(i -> proposals.stream()
                .filter(p -> p.getTenantId().equals(i.<UUID>getArgument(0)) && p.getContractId().equals(i.<UUID>getArgument(1))).toList());
        when(proposalRepo.findByTenantIdAndContractIdAndPartyIdOrderByCreatedAtAsc(any(), any(), any())).thenAnswer(i -> proposals.stream()
                .filter(p -> p.getTenantId().equals(i.<UUID>getArgument(0)) && p.getContractId().equals(i.<UUID>getArgument(1)) && p.getPartyId().equals(i.<UUID>getArgument(2))).toList());

        when(commentRepo.save(any())).thenAnswer(i -> { comments.add(i.getArgument(0)); return i.getArgument(0); });
        when(commentRepo.findByTenantIdAndId(any(), any())).thenAnswer(i -> comments.stream()
                .filter(c -> c.getTenantId().equals(i.<UUID>getArgument(0)) && c.getId().equals(i.<UUID>getArgument(1))).findFirst());
        when(commentRepo.findByTenantIdAndContractIdOrderByCreatedAtAsc(any(), any())).thenAnswer(i -> comments.stream()
                .filter(c -> c.getTenantId().equals(i.<UUID>getArgument(0)) && c.getContractId().equals(i.<UUID>getArgument(1))).toList());
        when(commentRepo.findByTenantIdAndContractIdAndInternalOnlyFalseOrderByCreatedAtAsc(any(), any())).thenAnswer(i -> comments.stream()
                .filter(c -> c.getTenantId().equals(i.<UUID>getArgument(0)) && c.getContractId().equals(i.<UUID>getArgument(1)) && !c.isInternalOnly()).toList());

        when(eventRepo.save(any())).thenAnswer(i -> { events.add(i.getArgument(0)); return i.getArgument(0); });
        when(eventRepo.findTop200ByTenantIdAndContractIdOrderByCreatedAtDesc(any(), any())).thenAnswer(i -> events.stream()
                .filter(e -> e.getTenantId().equals(i.<UUID>getArgument(0)) && e.getContractId().equals(i.<UUID>getArgument(1))).toList());

        when(linkRepo.save(any())).thenAnswer(i -> { ContractNegotiationLink l = i.getArgument(0); if (!links.contains(l)) links.add(l); return l; });
        when(linkRepo.findByTokenHash(anyString())).thenAnswer(i -> links.stream().filter(l -> l.getTokenHash().equals(i.<String>getArgument(0))).findFirst());
        when(linkRepo.findByTenantIdAndId(any(), any())).thenAnswer(i -> links.stream()
                .filter(l -> l.getTenantId().equals(i.<UUID>getArgument(0)) && l.getId().equals(i.<UUID>getArgument(1))).findFirst());
        when(linkRepo.findByTenantIdAndContractIdOrderByCreatedAtDesc(any(), any())).thenAnswer(i -> links.stream()
                .filter(l -> l.getTenantId().equals(i.<UUID>getArgument(0)) && l.getContractId().equals(i.<UUID>getArgument(1))).toList());
        when(linkRepo.findByTenantIdAndContractIdAndPartyIdAndRevokedAtIsNull(any(), any(), any())).thenAnswer(i -> links.stream()
                .filter(l -> l.getTenantId().equals(i.<UUID>getArgument(0)) && l.getContractId().equals(i.<UUID>getArgument(1))
                        && l.getPartyId().equals(i.<UUID>getArgument(2)) && l.getRevokedAt() == null).toList());
        when(linkRepo.revokeAllActive(any(), any(), anyString(), any())).thenAnswer(i -> {
            int n = 0;
            for (ContractNegotiationLink l : links)
                if (l.getTenantId().equals(i.<UUID>getArgument(0)) && l.getContractId().equals(i.<UUID>getArgument(1)) && l.getRevokedAt() == null) { l.revoke(i.getArgument(2)); n++; }
            return n;
        });
        when(sessionRepo.save(any())).thenAnswer(i -> { sessions.add(i.getArgument(0)); return i.getArgument(0); });
        when(sessionRepo.findBySessionHash(anyString())).thenAnswer(i -> sessions.stream().filter(s -> s.getSessionHash().equals(i.<String>getArgument(0))).findFirst());

        when(redline.versions(any(), any())).thenAnswer(i -> List.of(new VersionInfo(versions.computeIfAbsent(i.<UUID>getArgument(1), k -> new AtomicInteger(1)).get(), "X", null, null, Instant.now())));
        when(redline.snapshot(any(), any(), anyString(), any(), any())).thenAnswer(i -> {
            Contract c = i.getArgument(1);
            return versions.computeIfAbsent(c.getId(), k -> new AtomicInteger(1)).incrementAndGet();
        });
        when(otp.generateAndStore(anyString())).thenReturn("123456");
        when(otp.verify(anyString(), anyString())).thenAnswer(i -> "123456".equals(i.<String>getArgument(1)));
        when(sms.send(anyString(), anyString())).thenReturn(true);

        service = new ContractNegotiationService(contractRepo, partyRepo, negotiationRepo, proposalRepo, commentRepo, eventRepo, linkRepo, redline, hashing, publisher, approvals);
        access = new ContractNegotiationAccessService(contractRepo, partyRepo, negotiationRepo, linkRepo, sessionRepo, eventRepo, service, otp, limiter, email, sms);
        org.springframework.test.util.ReflectionTestUtils.setField(access, "baseUrl", "https://app.test");
        org.springframework.test.util.ReflectionTestUtils.setField(access, "linkValidityDays", 14);
        org.springframework.test.util.ReflectionTestUtils.setField(access, "sessionMinutes", 60);
    }

    private Optional<ContractNegotiation> negotiationOf(UUID tenant, UUID contractId) {
        return negotiations.stream().filter(n -> n.getTenantId().equals(tenant) && n.getContractId().equals(contractId)).findFirst();
    }

    /** A draft contract with two parties (one with a phone, one email only). */
    Contract contract(TenantId tenant, String title, String body) {
        Contract c = Contract.create(tenant, "CTR-" + (contracts.size() + 1), title, "SERVICE", body, null, UUID.randomUUID());
        contracts.add(c);
        parties.add(ContractParty.create(tenant, c.getId(), "COMPANY", "CLIENT", "Acme " + title, "a@acme.co", "0821234567", "Acme", 1));
        parties.add(ContractParty.create(tenant, c.getId(), "COMPANY", "SUPPLIER", "Beta " + title, "b@beta.co", null, "Beta", 2));
        return c;
    }

    ContractParty party(Contract c, int index) {
        return parties.stream().filter(p -> p.getContractId().equals(c.getId())).toList().get(index);
    }

    /** Runs a unit of work as one transaction: the contract row lock taken inside it is released when it ends. */
    <T> T inTx(Supplier<T> work) {
        try {
            return work.get();
        } finally {
            rowLocks.values().forEach(l -> { while (l.isHeldByCurrentThread()) l.unlock(); });
        }
    }

    void inTxDo(Runnable work) { inTx((Supplier<Object>) () -> { work.run(); return null; }); }

    List<ContractNegotiationProposal> proposalsOf(Contract c) {
        return new ArrayList<>(proposals.stream().filter(p -> p.getContractId().equals(c.getId())).toList());
    }
}
