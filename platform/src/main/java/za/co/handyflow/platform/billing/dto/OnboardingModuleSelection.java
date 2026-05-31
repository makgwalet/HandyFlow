package za.co.handyflow.platform.billing.dto;

import java.util.List;

// WHY separate DTO? Onboarding may activate multiple modules at once
// as part of registration — different from activating one at a time.
public record OnboardingModuleSelection(
        List<String> moduleKeys,   // ["security", "fleet", "hr"]
        int trialDays              // default 60
) {}