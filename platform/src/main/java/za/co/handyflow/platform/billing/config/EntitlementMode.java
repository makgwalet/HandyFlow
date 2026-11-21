package za.co.handyflow.platform.billing.config;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Decides whether a module's entitlement is enforced (refused) or only reported.
 * Enforced when the global flag is on, or when the module is named in handyflow.entitlement.enforce-modules
 * (comma-separated), so one module can be switched on without switching on every other. Modules with no
 * catalogue row ({@link ModuleEntitlementPaths#REPORT_ONLY}) are never enforced.
 */
final class EntitlementMode {

    private final boolean global;
    private final Set<String> modules;

    EntitlementMode(boolean global, String enforceModulesCsv) {
        this.global = global;
        this.modules = enforceModulesCsv == null ? Set.of()
                : Arrays.stream(enforceModulesCsv.split(","))
                        .map(s -> s.trim().toLowerCase(Locale.ROOT))
                        .filter(s -> !s.isEmpty())
                        .collect(Collectors.toUnmodifiableSet());
    }

    boolean enforces(String moduleKey) {
        if (!ModuleEntitlementPaths.enforceable(moduleKey)) return false;
        return global || modules.contains(moduleKey.toLowerCase(Locale.ROOT));
    }
}
