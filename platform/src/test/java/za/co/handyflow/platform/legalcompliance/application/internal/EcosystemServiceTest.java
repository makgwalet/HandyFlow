package za.co.handyflow.platform.legalcompliance.application.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.legalcompliance.dto.EcosystemDtos.Ecosystem;
import za.co.handyflow.platform.legalcompliance.dto.EcosystemDtos.ModuleView;
import za.co.handyflow.platform.shared.ComplianceSignal;
import za.co.handyflow.platform.shared.ComplianceSignal.Severity;
import za.co.handyflow.platform.shared.ComplianceSignalSource;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class EcosystemServiceTest {

    private final TenantId t = TenantId.of(UUID.randomUUID());

    private static ComplianceSignalSource source(String key, String name, List<ComplianceSignal> signals) {
        return new ComplianceSignalSource() {
            public String moduleKey() { return key; }
            public String moduleName() { return name; }
            public List<ComplianceSignal> signals(TenantId tenantId) { return signals; }
        };
    }

    private static ComplianceSignalSource failing(String key, String name) {
        return new ComplianceSignalSource() {
            public String moduleKey() { return key; }
            public String moduleName() { return name; }
            public List<ComplianceSignal> signals(TenantId tenantId) { throw new IllegalStateException("db down"); }
        };
    }

    private static ComplianceSignal sig(String module, String key, long n, Severity s) { return new ComplianceSignal(module, key, "thing(s)", n, s, "/" + module); }

    @Test @DisplayName("Modules appear by name, signals critical first, and the totals add up across modules")
    void combines() {
        var fleet = source("fleet", "Fleet", List.of(sig("fleet", "b-warn", 4, Severity.WARNING), sig("fleet", "a-crit", 2, Severity.CRITICAL)));
        var fuel = source("fuel", "Fuel", List.of(sig("fuel", "lic", 1, Severity.CRITICAL), sig("fuel", "info", 9, Severity.INFO)));
        Ecosystem e = new EcosystemService(List.of(fuel, fleet), (tn, k) -> true).overview(t);
        assertEquals("Fleet", e.modules().get(0).moduleName());
        assertEquals("CRITICAL", e.modules().get(0).signals().get(0).severity());
        assertEquals(3, e.critical());
        assertEquals(4, e.warnings());
    }

    @Test @DisplayName("A module the tenant does not have is not asked, and one that fails is shown as unavailable without breaking the others")
    void gatingAndIsolation() {
        var fleet = source("fleet", "Fleet", List.of(sig("fleet", "x", 1, Severity.CRITICAL)));
        var boom = failing("fuel", "Fuel");
        var hidden = source("hr", "HR", List.of(sig("hr", "y", 5, Severity.CRITICAL)));
        Ecosystem e = new EcosystemService(List.of(fleet, boom, hidden), (tn, k) -> !k.equals("hr")).overview(t);
        assertEquals(2, e.modules().size());
        ModuleView fuel = e.modules().stream().filter(m -> m.moduleKey().equals("fuel")).findFirst().orElseThrow();
        assertEquals("UNAVAILABLE", fuel.status());
        assertTrue(fuel.signals().isEmpty());
        assertEquals(1, e.critical());
        assertTrue(e.modules().stream().noneMatch(m -> m.moduleKey().equals("hr")));
    }

    @Test @DisplayName("With no sources at all the view is simply empty")
    void empty() {
        Ecosystem e = new EcosystemService(List.of(), (tn, k) -> true).overview(t);
        assertTrue(e.modules().isEmpty());
        assertEquals(0, e.critical());
    }

    @Test @DisplayName("A signal must have a count above zero, a label and a relative link")
    void signalValidation() {
        assertThrows(IllegalArgumentException.class, () -> new ComplianceSignal("fleet", "k", "x", 0, Severity.INFO, null));
        assertThrows(IllegalArgumentException.class, () -> new ComplianceSignal("fleet", "k", " ", 1, Severity.INFO, null));
        assertThrows(IllegalArgumentException.class, () -> new ComplianceSignal("fleet", "k", "x", 1, Severity.INFO, "https://evil.example"));
        assertDoesNotThrow(() -> new ComplianceSignal("fleet", "k", "x", 1, Severity.INFO, "/fleet/compliance"));
    }
}
