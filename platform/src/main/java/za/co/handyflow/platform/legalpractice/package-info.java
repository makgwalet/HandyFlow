/**
 * Legal Practice ({@code legalpractice}) — the outsourced-provider counterpart
 * to Module 1 ({@code legalcompliance}, internal-only: a tenant's own
 * regulatory/litigation/POPIA tracking). No dependency exists in either
 * direction between the two modules; {@code legalcompliance}'s own status
 * doc flagged this gap when it shipped, and this module closes it.
 * <p>
 * <b>What it models:</b> a law firm (the tenant) managing its own client
 * portfolio, matters, attorney time, trust accounting, and billing — for
 * firms of any size, from a sole practitioner to a multi-partner practice.
 * Structurally closest to {@code accountant} (practice-management shape:
 * profile &rarr; client portfolio &rarr; billable work &rarr; billing)
 * crossed with {@code collectionsagency} (trust-accounting pattern — the
 * first and only precedent in this codebase for a client-money trust
 * ledger before this module).
 * <p>
 * <b>Key domain shape</b> (see each entity's own Javadoc for the full
 * reasoning; the module's own {@code scope-decision-note.md} carries the
 * confirmed-vs-reasoned distinctions):
 * <ul>
 *   <li>{@code LpMatter} carries its own billing type (FIXED_FEE or
 *       HOURLY) independent of whether the client also holds an ongoing
 *       {@code LpRetainerAgreement} — both billing shapes can coexist.</li>
 *   <li>{@code LpTrustTransaction} is the compliance-critical centerpiece:
 *       RECEIPT / TRANSFER_TO_BUSINESS (always tied to a real invoice —
 *       the Legal Practice Act control) / DISBURSEMENT_PAYMENT / REFUND,
 *       each with its own factory-enforced required/forbidden field
 *       combination, mirrored by a DB {@code CHECK} constraint.</li>
 *   <li>{@code LpAttorney} is a free-standing staff entity with an
 *       optional, unvalidated HR {@code employeeId} link (not a hard
 *       {@code HrFacade} dependency) — a firm's admitted attorneys are
 *       routinely principals or consultants, not payroll employees.</li>
 * </ul>
 * <p>
 * <b>Cross-module integration:</b> {@code AccountingFacade} for real GL
 * posting (only where the firm's own earned revenue moves — an invoice
 * payment or a TRANSFER_TO_BUSINESS trust movement; RECEIPT/
 * DISBURSEMENT_PAYMENT/REFUND never touch the tenant's own ledger, since
 * that money was never the firm's revenue); {@code EvidenceFacade} for
 * matter/client documents (signed mandates, correspondence, court
 * filings); {@code notifications} for the daily key-date sweep.
 * {@code hr} IS declared here — corrected after an initial draft omitted
 * it while still calling {@code HrFacade.findEmployeeById()} from
 * {@code LpAttorneyService} for the optional employee-name/email
 * convenience lookup (see {@code LpAttorney}'s own Javadoc: the
 * {@code employeeId} link itself is optional and unvalidated, but a
 * best-effort facade call across module boundaries is still real
 * cross-module coupling as far as Spring Modulith's own verification is
 * concerned — declaring it here is what keeps that call architecturally
 * legal, matching how {@code training}/{@code agriculture} both declare
 * {@code hr} for their own, stronger, validated employee references).
 * Deliberately no {@code debtcollection}/{@code collectionsagency}/
 * {@code legalcompliance} dependency.
 */
@org.springframework.modulith.ApplicationModule(
        allowedDependencies = {"shared", "billing", "accounting", "evidence", "notifications", "hr"}
)
package za.co.handyflow.platform.legalpractice;
