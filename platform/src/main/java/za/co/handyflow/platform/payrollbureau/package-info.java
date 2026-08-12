
/**
 * HR/Payroll Bureau — outsourced-provider module for a payroll bureau
 * serving multiple external client businesses. Deliberately structured
 * the same way `accountant` is (see that module's own package-info for
 * the original 7-layer pattern this replicates): practice shell -> client
 * portfolio -> payroll core -> SARS compliance -> employee documents ->
 * billing -> portfolio ops.
 * <p>
 * WHY A SEPARATE MODULE, NOT A MODE ON `hr`: same reasoning already
 * established for accountant/accounting (HandyFlow BOS Discovery doc,
 * Section 3) — a bureau's clients aren't necessarily HandyFlow tenants
 * themselves, and the bureau needs its own client-portfolio/billing
 * layer that has no equivalent in single-tenant `hr`. Generalizing `hr`
 * into "internal or bureau mode" would bloat a working module for a
 * fundamentally different data shape, not simplify anything.
 * <p>
 * allowedDependencies mirrors what accountant's own module needs for the
 * same reasons: identity (TenantFacade, for branding reminder emails
 * with the bureau's own tenant details) and billing (FeatureGuard, since
 * this module is a separately-subscribable add-on, not bundled free with
 * `hr`).
 */
@ApplicationModule(allowedDependencies = {"shared", "identity", "billing"})
package za.co.handyflow.platform.payrollbureau;

import org.springframework.modulith.ApplicationModule;