// FIX (business decision, see PLATFORM-ENGINES-PROGRESS.md / strategic
// roadmap backlog Part 0, Decision 2): "tenant branding is a platform
// capability, not a module-by-module exception" — resolved by the
// business. Added "identity" specifically so ApRemittanceEmailService
// can fetch the tenant's own company name (TenantFacade) and brand
// remittance advices as coming from the tenant, not HandyFlow — the
// module previously couldn't do this at all, and defaulting to no
// branding (rather than incorrectly defaulting to HandyFlow branding)
// was the safer choice until this decision was made.
@ApplicationModule(allowedDependencies = {"shared", "identity", "notifications", "accounting", "approvals"})
package za.co.handyflow.platform.ap;

import org.springframework.modulith.ApplicationModule;