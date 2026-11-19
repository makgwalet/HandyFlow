package za.co.handyflow.platform.contracting.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.contracting.domain.model.Contract;
import za.co.handyflow.platform.contracting.dto.ContractNegotiationDtos.ProposalInput;
import za.co.handyflow.platform.contracting.dto.ContractNegotiationDtos.ProposalView;
import za.co.handyflow.platform.shared.TenantId;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Concurrency and lock order. The harness models the contract row lock (held from findForUpdate until the unit of work ends), so these
 * tests exercise the real service logic under genuine thread interleaving. They cannot prove database behaviour; they prove that the
 * service takes the lock first and re-reads state after it, which is what makes the database lock effective.
 */
class ContractNegotiationConcurrencyTest {

    static final String P1 = "<p>Payment is due in 30 days.</p>", P2 = "<p>Either party may terminate on 60 days notice.</p>";
    static final Path SERVICE = Path.of("src/main/java/za/co/handyflow/platform/contracting/application/internal/ContractNegotiationService.java");
    static final Path CONTRACTING = Path.of("src/main/java/za/co/handyflow/platform/contracting/application/internal/ContractingService.java");

    NegotiationHarness h;
    ContractNegotiationService service;
    TenantId tenant = TenantId.generate();
    UUID staff = UUID.randomUUID();
    Contract c;
    ExecutorService pool;

    @BeforeEach
    void setUp() {
        h = new NegotiationHarness();
        service = h.service;
        c = h.contract(tenant, "Race", P1 + P2);
        h.inTxDo(() -> service.open(tenant, c.getId(), LocalDate.now().plusDays(10), staff));
        pool = Executors.newFixedThreadPool(4);
    }

    private ProposalView propose(int party, String kind, int block, String original, String proposed) {
        return h.inTx(() -> service.submitProposal(tenant, c.getId(), h.party(c, party).getId(), new ProposalInput(kind, block, original, proposed, "r"), "1.1.1.1"));
    }

    /** Runs both tasks at the same instant and returns how many succeeded; any failure must be a clean application error. */
    private int race(Callable<?> a, Callable<?> b) throws Exception {
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();
        for (Callable<?> task : List.of(a, b)) {
            results.add(pool.submit(() -> {
                go.await();
                try { task.call(); return true; }
                catch (IllegalStateException | za.co.handyflow.platform.shared.HandyFlowException e) { return false; }
            }));
        }
        go.countDown();
        int ok = 0;
        for (Future<Boolean> f : results) if (f.get(10, TimeUnit.SECONDS)) ok++;
        pool.shutdown();
        assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));
        return ok;
    }

    @Test
    void twoPartiesProposingAtOnceBothLand() throws Exception {
        int ok = race(() -> propose(0, "DELETE", 0, P1, null), () -> propose(1, "DELETE", 1, P2, null));
        assertEquals(2, ok);
        assertEquals(2, h.proposalsOf(c).size());
    }

    @Test
    void acceptAndRejectOfOneProposalHaveExactlyOneWinner() throws Exception {
        ProposalView p = propose(0, "REPLACE", 0, P1, "<p>Payment is due in 14 days.</p>");
        int ok = race(() -> h.inTx(() -> service.accept(tenant, c.getId(), p.id(), null, staff)),
                () -> h.inTx(() -> service.reject(tenant, c.getId(), p.id(), "no", staff)));
        assertEquals(1, ok, "a proposal is decided once");
        String status = h.proposalsOf(c).get(0).getStatus();
        assertTrue(status.equals("ACCEPTED") || status.equals("REJECTED"), status);
    }

    @Test
    void twoAppliesOfTheSameAcceptedSetHaveExactlyOneWinner() throws Exception {
        ProposalView p = propose(0, "REPLACE", 0, P1, "<p>Payment is due in 14 days.</p>");
        h.inTx(() -> service.accept(tenant, c.getId(), p.id(), null, staff));
        int ok = race(() -> h.inTx(() -> service.applyAccepted(tenant, c.getId(), null, staff)),
                () -> h.inTx(() -> service.applyAccepted(tenant, c.getId(), null, staff)));
        assertEquals(1, ok, "the second apply finds nothing accepted left to apply");
        assertEquals("APPLIED", h.proposalsOf(c).get(0).getStatus());
    }

    @Test
    void twoAcceptsOfTheSameProposalHaveExactlyOneWinner() throws Exception {
        ProposalView p = propose(0, "DELETE", 0, P1, null);
        int ok = race(() -> h.inTx(() -> service.accept(tenant, c.getId(), p.id(), null, staff)),
                () -> h.inTx(() -> service.accept(tenant, c.getId(), p.id(), null, staff)));
        assertEquals(1, ok);
    }

    @Test
    void aProposalCannotSlipInAfterTheNegotiationIsClosed() throws Exception {
        int ok = race(() -> h.inTx(() -> service.close(tenant, c.getId(), "done", staff)),
                () -> propose(0, "DELETE", 0, P1, null));
        // Either order is valid, and exactly one of them wins: if the proposal got in first the close is refused (a proposal is waiting),
        // and if the close came first the proposal is refused. What must never happen is a proposal stored against a closed negotiation.
        boolean closed = "CLOSED".equals(h.negotiations.get(0).getStatus());
        boolean stored = !h.proposalsOf(c).isEmpty();
        assertFalse(closed && stored, "a proposal must never be stored against a closed negotiation");
        assertTrue(closed || stored);
        assertEquals(1, ok);
    }

    // ── Lock order ───────────────────────────────────────────────────────────

    @Test
    void everyServiceMethodLocksTheContractBeforeTheNegotiation() throws Exception {
        String src = Files.readString(SERVICE);
        String[] chunks = src.split("\\n    public ");
        int checked = 0;
        for (String chunk : chunks) {
            int neg = chunk.indexOf("lockNegotiation(");
            if (neg < 0) continue;
            int con = chunk.indexOf("lockContract(");
            String head = chunk.substring(0, Math.min(60, chunk.length()));
            assertTrue(con >= 0 && con < neg, "contract must be locked before the negotiation in: " + head);
            checked++;
        }
        assertTrue(checked >= 10, "expected to check every mutating method, found " + checked);
    }

    @Test
    void sendingLocksTheContractFirstAndFreezesOnlyAfterTheChecks() throws Exception {
        String src = Files.readString(CONTRACTING);
        int start = src.indexOf("public ContractResponse sendForSigning(");
        assertTrue(start > 0);
        String body = src.substring(start, src.indexOf("\n    public ", start + 10));
        int lock = body.indexOf("findForUpdate(");
        int check = body.indexOf("assertReadyForSigning(");
        int send = body.indexOf("c.send(");
        int freeze = body.indexOf("freezeOnSend(");
        assertTrue(lock >= 0 && lock < check, "lock before the readiness check");
        assertTrue(check < send, "readiness check before the contract is sent");
        assertTrue(send < freeze, "negotiation frozen only once the contract is sent");
        assertFalse(body.contains("findActiveById("), "the send must not use an unlocked read");
    }

    @Test
    void theHarnessRowLockIsReleasedWhenAUnitOfWorkEnds() {
        h.inTxDo(() -> service.staffView(tenant, c.getId()));
        h.inTxDo(() -> service.markReady(tenant, c.getId(), staff));
        assertEquals("READY_FOR_SIGNATURE", h.inTx(() -> service.staffView(tenant, c.getId())).negotiation().status());
    }
}
