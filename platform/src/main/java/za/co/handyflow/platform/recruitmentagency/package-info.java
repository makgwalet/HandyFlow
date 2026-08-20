/**
 * Recruitment Agency — outsourced-provider module for an agency finding
 * and placing candidates with external client businesses. Mirrors the
 * HR/Payroll Bureau pattern (see that module's own package-info for the
 * full reasoning this replicates): practice shell -> client portfolio ->
 * core domain work -> billing -> client portal.
 * <p>
 * WHY A SEPARATE MODULE, NOT A MODE ON `recruiter`: same reasoning as
 * accountant/accounting and payrollbureau/hr — an agency's clients
 * aren't HandyFlow tenants themselves, and the agency needs its own
 * client-portfolio/billing layer with no equivalent in single-tenant
 * `recruiter`. `recruiter` stays untouched and isolated; this module
 * mirrors its proven domain shape (job -> candidate -> pipeline ->
 * interview) without importing its internal classes.
 * <p>
 * REAL DIFFERENCE FROM PAYROLL BUREAU, NOT JUST A RELABEL: when a
 * candidate is placed, the CLIENT employs them, not the agency — there
 * is no HrFacade.createEmployee()-equivalent step here at all. Billing
 * is placement-fee-based (commonly a percentage of first-year salary),
 * not a recurring per-employee-per-month charge. Both of these shape
 * the domain model differently than PayClient/PayFeeNote did — not
 * simply copied with names swapped.
 */
@ApplicationModule(allowedDependencies = {"shared", "identity", "billing", "evidence"})
package za.co.handyflow.platform.recruitmentagency;

import org.springframework.modulith.ApplicationModule;