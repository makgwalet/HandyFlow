// FIX (business decision, see PLATFORM-ENGINES-PROGRESS.md / strategic
// roadmap backlog Part 0, Decision 2): "tenant branding is a platform
// capability, not a module-by-module exception" — resolved by the
// business. Added "identity" specifically so MarketingService can fetch
// the tenant's own company name (TenantFacade) and brand
// buildUnsubscribeConfirmationEmail as coming from the tenant, not a
// brand-neutral document — same fix already applied to ap for the
// equivalent reason (ApRemittanceEmailService).
@ApplicationModule(allowedDependencies = {"shared", "identity", "crm"})
package za.co.handyflow.platform.marketing;

import org.springframework.modulith.ApplicationModule;
