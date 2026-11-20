package za.co.handyflow.platform.legalcompliance.application.internal;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.legalcompliance.dto.EcosystemDtos.*;
import za.co.handyflow.platform.shared.ComplianceSignal;
import za.co.handyflow.platform.shared.ComplianceSignal.Severity;
import za.co.handyflow.platform.shared.ComplianceSignalSource;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.BiPredicate;

/**
 * The cross-module compliance view: whatever each HandyFlow module reports through {@link ComplianceSignalSource}. This module imports none of them, so adding a module
 * is a matter of that module publishing a source bean. Only modules the tenant has are asked. One module failing never blanks the view: it is shown as unavailable.
 * Nothing is stored and no figure here feeds the executive indicators or snapshots; these counts belong to their modules and are shown, not re-counted.
 */
@Slf4j
@Service
public class EcosystemService {

    private final List<ComplianceSignalSource> sources;
    private final BiPredicate<TenantId, String> hasModule;

    @Autowired
    public EcosystemService(ObjectProvider<ComplianceSignalSource> sources, FeatureGuard guard) {
        this(sources.orderedStream().toList(), guard::hasModuleFor);
    }

    EcosystemService(List<ComplianceSignalSource> sources, BiPredicate<TenantId, String> hasModule) {
        this.sources = sources;
        this.hasModule = hasModule;
    }

    public Ecosystem overview(TenantId t) {
        List<ModuleView> out = new ArrayList<>();
        for (ComplianceSignalSource s : sources) {
            String key = s.moduleKey(), name = s.moduleName();
            try {
                if (!hasModule.test(t, key)) continue;
                List<Signal> signals = s.signals(t).stream().filter(c -> c.count() > 0)
                        .sorted(Comparator.comparingInt((ComplianceSignal c) -> c.severity().ordinal()).thenComparing(ComplianceSignal::key))
                        .map(c -> new Signal(c.key(), c.label(), c.count(), c.severity().name(), c.actionUrl())).toList();
                out.add(new ModuleView(key, name, "OK", signals));
            } catch (Exception e) {
                log.warn("[Legal/Compliance] Signal source '{}' failed for tenant={}: {}", key, t.getValue(), e.getMessage());
                out.add(new ModuleView(key, name, "UNAVAILABLE", List.of()));
            }
        }
        out.sort(Comparator.comparing(ModuleView::moduleName));
        long critical = count(out, Severity.CRITICAL), warnings = count(out, Severity.WARNING);
        return new Ecosystem(Instant.now(), out, critical, warnings);
    }

    private static long count(List<ModuleView> modules, Severity s) {
        return modules.stream().flatMap(m -> m.signals().stream()).filter(x -> x.severity().equals(s.name())).mapToLong(Signal::count).sum();
    }
}
