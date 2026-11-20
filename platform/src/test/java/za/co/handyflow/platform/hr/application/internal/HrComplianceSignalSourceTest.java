package za.co.handyflow.platform.hr.application.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.hr.domain.repository.HrComplianceCounts;
import za.co.handyflow.platform.shared.ComplianceSignal;
import za.co.handyflow.platform.shared.ComplianceSignal.Severity;
import za.co.handyflow.platform.shared.TenantId;

import java.lang.reflect.Proxy;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class HrComplianceSignalSourceTest {

    private static final TenantId T = TenantId.of(UUID.randomUUID());
    private final Map<String, Long> answers = new HashMap<>();
    private final List<Object[]> calls = new ArrayList<>();

    private HrComplianceSignalSource source() {
        HrComplianceCounts repo = (HrComplianceCounts) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{HrComplianceCounts.class}, (p, m, a) -> {
            if (m.getName().equals("toString")) return "counts";
            calls.add(a);
            return answers.getOrDefault(m.getName(), 0L);
        });
        return new HrComplianceSignalSource(repo);
    }

    private static ComplianceSignal by(List<ComplianceSignal> l, String key) { return l.stream().filter(s -> s.key().equals(key)).findFirst().orElseThrow(); }

    @Test @DisplayName("Nothing to report means no signals at all (never a zero)")
    void nothing() {
        assertTrue(source().signals(T).isEmpty());
        assertEquals("hr", source().moduleKey());
    }

    @Test @DisplayName("An expired work permit is critical; the rest are warnings or information; all link to the employee register")
    void severities() {
        answers.putAll(Map.of("workPermitsExpired", 2L, "workPermitsExpiring", 3L, "passportsExpired", 1L, "passportsExpiring", 4L));
        List<ComplianceSignal> s = source().signals(T);
        assertEquals(4, s.size());
        assertEquals(Severity.CRITICAL, by(s, "workPermitsExpired").severity());
        assertEquals(2L, by(s, "workPermitsExpired").count());
        assertEquals(Severity.WARNING, by(s, "workPermitsExpiring").severity());
        assertEquals(Severity.WARNING, by(s, "passportsExpired").severity());
        assertEquals(Severity.INFO, by(s, "passportsExpiring").severity());
        assertTrue(s.stream().allMatch(x -> x.actionUrl().equals("/hr/employees") && x.module().equals("hr")));
        assertTrue(by(s, "workPermitsExpiring").label().contains("within 30 days"));
    }

    @Test @DisplayName("Only the signals with a count appear")
    void partial() {
        answers.put("passportsExpiring", 5L);
        List<ComplianceSignal> s = source().signals(T);
        assertEquals(List.of("passportsExpiring"), s.stream().map(ComplianceSignal::key).toList());
    }

    @Test @DisplayName("The queries get this tenant, today in Johannesburg and a 30-day window")
    void arguments() {
        source().signals(T);
        LocalDate today = LocalDate.now(ZoneId.of("Africa/Johannesburg"));
        assertEquals(4, calls.size());
        for (Object[] a : calls) {
            assertEquals(T.getValue(), ((TenantId) a[0]).getValue());
            assertEquals(today, a[1]);
            if (a.length == 3) assertEquals(today.plusDays(30), a[2]);
        }
    }
}
