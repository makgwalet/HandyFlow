package za.co.handyflow.platform.legalpractice.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.legalpractice.domain.model.*;
import za.co.handyflow.platform.legalpractice.domain.repository.*;
import za.co.handyflow.platform.legalpractice.dto.*;
import za.co.handyflow.platform.shared.TenantId;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

/** That opening a matter and taking money into trust really ask the due diligence gate first, and that nothing is written when it refuses. */
class LpKycGateWiringTest {

    private final TenantId firm = TenantId.of(UUID.randomUUID());
    private final Map<UUID, LpClient> clients = new HashMap<>();
    private final Map<UUID, LpMatter> matters = new HashMap<>();
    private final List<LpTrustTransaction> ledger = new ArrayList<>();
    private final List<String> asked = new ArrayList<>();
    private boolean refuse;

    private final LpKycGate gate = (tenantId, clientId, action) -> {
        asked.add(action + ":" + clientId);
        if (refuse) throw new IllegalStateException("not verified, so you cannot " + action);
    };

    private LpTrustTransactionService trust;
    private LpMatterService matterService;
    private LpClient client;

    @SuppressWarnings("unchecked")
    private static <T> T fake(Class<T> type, Map<String, Function<Object[], Object>> handlers) {
        InvocationHandler h = (proxy, m, args) -> {
            Function<Object[], Object> f = handlers.get(m.getName());
            if (f != null) return f.apply(args == null ? new Object[0] : args);
            if (m.isDefault()) return InvocationHandler.invokeDefault(proxy, m, args);
            throw new UnsupportedOperationException(m.getName());
        };
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, h);
    }

    @BeforeEach
    void setUp() {
        clients.clear(); matters.clear(); ledger.clear(); asked.clear(); refuse = false;
        var clientRepo = fake(LpClientRepository.class, Map.of(
                "findActiveById", a -> Optional.ofNullable(clients.get((UUID) a[1])).filter(c -> c.getTenantId().equals(a[0])),
                "save", a -> { LpClient c = (LpClient) a[0]; clients.put(c.getId(), c); return c; }));
        var matterRepo = fake(LpMatterRepository.class, Map.of(
                "findActiveById", a -> Optional.ofNullable(matters.get((UUID) a[1])).filter(m -> m.getTenantId().equals(a[0])),
                "save", a -> { LpMatter m = (LpMatter) a[0]; matters.put(m.getId(), m); return m; }));
        var trustRepo = fake(LpTrustTransactionRepository.class, Map.of(
                "save", a -> { ledger.add((LpTrustTransaction) a[0]); return a[0]; },
                "findByIdempotencyKey", a -> ledger.stream().filter(t -> t.getTenantId().equals(a[0]) && a[1].equals(t.getIdempotencyKey())).findFirst()));
        var poster = new LpAccountingPoster(null);
        trust = new LpTrustTransactionService(trustRepo, clientRepo, null, matterRepo, poster, LpWallGuard.NONE, gate);
        matterService = new LpMatterService(matterRepo, trustRepo, null, LpWallGuard.NONE, gate);
        client = LpClient.create(firm, "Dlamini Trading", null, null, "COMPANY", null, null);
        clients.put(client.getId(), client);
    }

    private LpTrustTransactionResponse receipt(String key) {
        return trust.recordReceipt(firm, client.getId(), new RecordTrustReceiptRequest(null, new BigDecimal("500.00"), null, null, null, key), UUID.randomUUID(), "Clerk");
    }

    @Test
    void aReceiptAsksTheGateAboutThatClient() {
        receipt(null);
        assertEquals(List.of("receive money into trust:" + client.getId()), asked);
        assertEquals(1, ledger.size());
    }

    @Test
    void aRefusedReceiptWritesNothingAndLeavesTheBalanceAlone() {
        refuse = true;
        var e = assertThrows(IllegalStateException.class, () -> receipt(null));
        assertTrue(e.getMessage().contains("receive money into trust"));
        assertTrue(ledger.isEmpty());
        assertEquals(0, BigDecimal.ZERO.compareTo(client.getTrustBalance()));
    }

    @Test
    void aReplayedReceiptIsNotGatedAgainBecauseItWritesNothing() {
        receipt("key-1");
        refuse = true;
        assertDoesNotThrow(() -> receipt("key-1"));   // the same request returns what was recorded
        assertEquals(1, ledger.size());
    }

    @Test
    void openingAMatterAsksTheGateBeforeAnythingElse() {
        refuse = true;
        var req = new CreateLpMatterRequest(client.getId(), UUID.randomUUID(), "M-1", "LITIGATION", "Dlamini v Nkosi", null, "HOURLY", null,
                LocalDate.now(), null, UUID.randomUUID());
        var e = assertThrows(IllegalStateException.class, () -> matterService.createMatter(firm, req));
        assertTrue(e.getMessage().contains("open a matter"));
        assertEquals(List.of("open a matter:" + client.getId()), asked);
        assertTrue(matters.isEmpty());
    }
}
