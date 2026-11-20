package za.co.handyflow.platform.accountant.application.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.accountant.domain.repository.AccFicaComplianceCounts;
import za.co.handyflow.platform.shared.ComplianceSignal;
import za.co.handyflow.platform.shared.ComplianceSignal.Severity;
import za.co.handyflow.platform.shared.TenantId;

import java.lang.reflect.Proxy;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class AccComplianceSignalSourceTest {

    private static final TenantId T = TenantId.of(UUID.randomUUID());
    private final Map<String, Long> answers = new HashMap<>();
    private final List<Object[]> calls = new ArrayList<>();

    private AccComplianceSignalSource source() {
        AccFicaComplianceCounts repo = (AccFicaComplianceCounts) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{AccFicaComplianceCounts.class}, (p, m, a) -> {
            if (m.getName().equals("toString")) return "counts";
            calls.add(a);
            return answers.getOrDefault(m.getName(), 0L);
        });
        return new AccComplianceSignalSource(repo);
    }

    @Test @DisplayName("Nothing to report means no signals, and the module key is the billing key")
    void nothing() {
        assertTrue(source().signals(T).isEmpty());
        assertEquals("accountant", source().moduleKey());
    }

    @Test @DisplayName("Expired and expiring FICA documents are both warnings and link to clients")
    void severities() {
        answers.putAll(Map.of("expiredNotReplaced", 4L, "expiringNotReplaced", 2L));
        List<ComplianceSignal> s = source().signals(T);
        assertEquals(2, s.size());
        assertTrue(s.stream().allMatch(x -> x.severity() == Severity.WARNING && x.actionUrl().equals("/accountant/clients")));
        assertEquals(4L, s.stream().filter(x -> x.key().equals("ficaExpired")).findFirst().orElseThrow().count());
        assertEquals(2L, s.stream().filter(x -> x.key().equals("ficaExpiring")).findFirst().orElseThrow().count());
    }

    @Test @DisplayName("Only the signal with a count appears")
    void partial() {
        answers.put("expiringNotReplaced", 1L);
        assertEquals(List.of("ficaExpiring"), source().signals(T).stream().map(ComplianceSignal::key).toList());
    }

    @Test @DisplayName("The queries get this tenant's id, today in Johannesburg and a 30-day window")
    void arguments() {
        source().signals(T);
        LocalDate today = LocalDate.now(ZoneId.of("Africa/Johannesburg"));
        assertEquals(2, calls.size());
        for (Object[] a : calls) { assertEquals(T.getValue(), a[0]); assertEquals(today, a[1]); if (a.length == 3) assertEquals(today.plusDays(30), a[2]); }
    }
}
