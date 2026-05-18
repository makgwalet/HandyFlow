package za.co.handyflow.platform;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

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

    @Test
    void verifiesModuleStructure() {
        modules.verify();
    }

    @Test
    void generateModuleDocumentation() {
        new Documenter(modules)
                .writeModulesAsPlantUml()
                .writeIndividualModulesAsPlantUml();
    }
}