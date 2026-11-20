package za.co.handyflow.platform.supplychain.application.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.shared.ComplianceSignal;
import za.co.handyflow.platform.shared.ComplianceSignal.Severity;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.supplychain.domain.repository.ScSupplierComplianceCounts;

import java.lang.reflect.Proxy;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class ScComplianceSignalSourceTest {

    private static final TenantId T = TenantId.of(UUID.randomUUID());
    private final Map<String, Long> answers = new HashMap<>();
    private final List<Object[]> calls = new ArrayList<>();

    private ScComplianceSignalSource source() {
        ScSupplierComplianceCounts repo = (ScSupplierComplianceCounts) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{ScSupplierComplianceCounts.class}, (p, m, a) -> {
            if (m.getName().equals("toString")) return "counts";
            calls.add(a);
            return answers.getOrDefault(m.getName(), 0L);
        });
        return new ScComplianceSignalSource(repo);
    }

    @Test @DisplayName("Nothing to report means no signals, and the module key is the billing key")
    void nothing() {
        assertTrue(source().signals(T).isEmpty());
        assertEquals("supply_chain", source().moduleKey());
    }

    @Test @DisplayName("Expired certificates are a warning, expiring ones information; both link to suppliers")
    void severities() {
        answers.putAll(Map.of("bbbeeExpired", 3L, "bbbeeExpiring", 1L));
        List<ComplianceSignal> s = source().signals(T);
        assertEquals(2, s.size());
        ComplianceSignal expired = s.stream().filter(x -> x.key().equals("bbbeeExpired")).findFirst().orElseThrow();
        assertEquals(Severity.WARNING, expired.severity());
        assertEquals(3L, expired.count());
        assertEquals(Severity.INFO, s.stream().filter(x -> x.key().equals("bbbeeExpiring")).findFirst().orElseThrow().severity());
        assertTrue(s.stream().allMatch(x -> x.actionUrl().equals("/supply-chain/suppliers")));
    }

    @Test @DisplayName("Only the signal with a count appears")
    void partial() {
        answers.put("bbbeeExpired", 2L);
        assertEquals(List.of("bbbeeExpired"), source().signals(T).stream().map(ComplianceSignal::key).toList());
    }

    @Test @DisplayName("The queries get this tenant's id, today in Johannesburg and a 30-day window")
    void arguments() {
        source().signals(T);
        LocalDate today = LocalDate.now(ZoneId.of("Africa/Johannesburg"));
        assertEquals(2, calls.size());
        for (Object[] a : calls) { assertEquals(T.getValue(), a[0]); assertEquals(today, a[1]); if (a.length == 3) assertEquals(today.plusDays(30), a[2]); }
    }
}
