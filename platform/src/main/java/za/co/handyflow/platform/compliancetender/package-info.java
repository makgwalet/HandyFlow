/**
 * compliancetender — "Business Compliance & Tender": the internal-tenant
 * module answering "are we registered, compliant, and ready to tender?"
 * for the tenant's own business (CIPC, SARS/TCS, UIF, PSIRA, CSD, cidb,
 * NHBRC registrations; document vault; expiry calendar; eventually
 * tender readiness and response building).
 * <p>
 * SCOPE (Phase 1 only — see the strategic roadmap backlog, Part 6, for
 * the full phased plan): compliance tracking, document vault, and expiry
 * alerts. Deliberately does NOT yet include tender workspace, tender
 * response building, or cross-module reference data (project experience,
 * personnel, equipment, financials) — those are later phases, added when
 * there's real code that needs them, not declared as dependencies ahead
 * of that, the same discipline this codebase's other modules follow.
 * <p>
 * NOT a duplicate of {@code legalcompliance} — that module covers POPIA/
 * DSAR/litigation/regulatory-obligation compliance, a different domain
 * from business-registration and tender-readiness compliance. The
 * similar name is coincidental to both modules independently using the
 * word "compliance" for their own (different) subject matter.
 * <p>
 * NOT a duplicate of {@code security}'s existing
 * {@code PsiraComplianceScheduler} either — that tracks individual
 * guards' own PSiRA registration numbers (an operational, per-guard
 * check). This module's PSIRA workspace tracks the tenant's own company
 * PSIRA business registration — a different granularity of the same
 * regulator. The two are a real integration point once this module's
 * PSIRA workspace exists (a security company's tender readiness
 * plausibly wants to reference how many registered guards Security
 * already tracks), not a duplication to resolve.
 * <p>
 * allowedDependencies, Phase 1:
 * <ul>
 *   <li>{@code shared} — TenantId/TenantContext plumbing, every module's baseline.</li>
 *   <li>{@code identity} — {@code TenantNumberingFacade} for CBS-prefixed
 *       document numbers ({@code CBS-CLI-2026-000001} etc.), and
 *       {@code TenantFacade} for the tenant's own company name on
 *       generated documents (Business Compliance Passport, etc.).</li>
 *   <li>{@code evidence} — {@code EvidenceFacade} IS this module's
 *       document vault; confirmed already source-module-tagged and
 *       reusable (already has three independent consumers: Expenses,
 *       Recruitment Agency, Payroll Bureau). No second document-storage
 *       engine gets built here.</li>
 *   <li>{@code billing} — {@code FeatureGuard}, since this is a
 *       separately-subscribable module like every other optional one.</li>
 *   <li>{@code notifications} — expiry/deadline alerts route through the
 *       same {@code NotificationService} + {@code TenantAdminRecipients}
 *       pipeline {@code security.PsiraComplianceScheduler} already uses,
 *       not a new delivery mechanism.</li>
 * </ul>
 * Later phases add {@code projects}, {@code hr}, {@code fleet},
 * {@code accounting}, {@code supplychain} — deliberately not declared
 * yet, added when the tender-integration phase actually needs them.
 */
@ApplicationModule(allowedDependencies = {"shared", "identity", "evidence", "billing", "notifications"})
package za.co.handyflow.platform.compliancetender;

import org.springframework.modulith.ApplicationModule;
