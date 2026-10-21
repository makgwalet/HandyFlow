package za.co.handyflow.platform.hr.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.hr.EmployeeCreatedEvent;

import java.util.Set;

/**
 * Starts the onboarding of every new employee, however they were created (by hand, or converted from a Recruiter application).
 * It runs after the employee is saved, in its own transaction, so a problem here can never undo or block creating the employee.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class OnboardingEventHandler {

    private final OnboardingService onboardingService;

    @ApplicationModuleListener
    void on(EmployeeCreatedEvent event) {
        // A failure here leaves the event publication incomplete (Modulith keeps it to be resubmitted); it never touches the employee.
        // Onboarding can also be started by hand for the employee.
        onboardingService.startFor(event.tenantId(), event.employeeId(), null, Set.of());
        log.info("Onboarding started for new employee {}", event.employeeId());
    }
}
