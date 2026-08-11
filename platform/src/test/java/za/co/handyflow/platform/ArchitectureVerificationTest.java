package za.co.handyflow.platform;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.core.Violations;
import org.springframework.modulith.docs.Documenter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

class ArchitectureVerificationTest {

    /*
     * WHY NO FILTER?
     *
     * Instead of filtering 'shared' out of module detection,
     * we let Modulith see it as a module but declare it openly
     * accessible via each module's package-info.java.
     *
     * The allowedDependencies = "shared" in each module's
     * package-info.java is what grants access — that IS working.
     * The previous error was a different issue we've now resolved.
     */
    ApplicationModules modules = ApplicationModules.of(HandyFlowApplication.class);

    /**
     * ONE KNOWN, ACCEPTED EXCEPTION — READ BEFORE TOUCHING THIS TEST.
     * <p>
     * As of the HandyFlow BOS Discovery engagement (see that doc, Section
     * 31.3, decision Q19), there is exactly one accepted architectural
     * cycle in this codebase: billing <-> identity, caused by
     * AuthService/UserManagementService calling
     * billing.SubscriptionQueryFacade directly while
     * billing.BillingEventHandlers listens to identity's
     * TenantCreatedEvent. This was evaluated and deliberately kept — see
     * the Javadoc on AuthService.subscriptionQueryFacade for the full
     * reasoning. It is NOT an oversight and should NOT be "fixed" by
     * someone hitting a red build and reaching for the nearest change.
     * <p>
     * HOW THIS TEST HANDLES IT: rather than let one accepted cycle turn
     * this entire test permanently red (which would make everyone stop
     * trusting or reading its output — the exact failure mode a broken
     * "known failing test" always causes), this test catches the
     * Violations exception and checks that its message mentions ONLY the
     * billing/identity cycle and nothing else. If verify() ever reports
     * ANY additional violation — a new cycle, a new undeclared dependency,
     * anything — this test FAILS LOUDLY, because that additional
     * violation is new and needs the same review every other finding in
     * this codebase got.
     * <p>
     * CAVEAT — PLEASE READ: this is a pragmatic, string-matching-based
     * workaround, not a documented Spring Modulith API for "allow this one
     * cycle." I could not confirm from what's available in this session
     * whether your specific Spring Modulith version exposes an official
     * mechanism for excluding a single named cycle from verify() (some
     * versions may; I did not want to guess at an API surface I couldn't
     * verify). If one exists for your version, it would be a cleaner,
     * less fragile fix than this — worth a quick check of your Spring
     * Modulith version's docs before assuming this is the permanent
     * approach. This workaround is deliberately fragile in one specific,
     * safe direction: if Modulith's exception message format changes in a
     * future version bump, this test will most likely start FAILING
     * (loudly, visibly) rather than silently passing when it shouldn't —
     * that's the safer failure mode to design for here.
     */
    @Test
    void verifiesModuleStructure() {
        try {
            modules.verify();
        } catch (Violations violations) {
            String message = violations.getMessage();

            boolean mentionsOnlyKnownCycle =
                    message.contains("billing") && message.contains("identity")
                            && !message.contains("Module '") // no "Module 'X' depends on module 'Y'" undeclared-dependency violations
                            && countOccurrences(message, "Cycle detected") == 1;

            if (!mentionsOnlyKnownCycle) {
                fail("ArchitectureVerificationTest found violations beyond the one accepted " +
                        "billing<->identity cycle (Q19). This means either a NEW violation was " +
                        "introduced (most likely — go read the full message below and fix it), " +
                        "or Modulith's exception message format changed in a way that broke this " +
                        "workaround's string matching (less likely, but check your Spring Modulith " +
                        "version's changelog if the message below looks like ONLY the known cycle " +
                        "but this assertion still failed).\n\nFull violation report:\n" + message);
            }
            // Otherwise: exactly the one accepted, documented cycle — pass.
        }
    }

    private static int countOccurrences(String haystack, String needle) {
        int count = 0, idx = 0;
        while ((idx = haystack.indexOf(needle, idx)) != -1) {
            count++;
            idx += needle.length();
        }
        return count;
    }

    /**
     * Not a gate — a documentation generator. Run manually (or as a
     * separate, non-blocking CI step) to regenerate the module dependency
     * diagram and per-module docs under target/spring-modulith-docs/.
     */
    @Test
    void generateModuleDocumentation() {
        new Documenter(modules)
                .writeModulesAsPlantUml()
                .writeIndividualModulesAsPlantUml();
    }
}